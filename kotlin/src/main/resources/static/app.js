const $ = (selector) => document.querySelector(selector);
const views = [$("#welcome"), $("#join"), $("#game")];
const demoShowcase = $("#game-demos");
const sessionKey = (gameId) => `boardgame-rpc:${gameId}`;

let gameState = null;
let identity = null;
let botThinking = false;
let pollTimer = null;
let lastEventSequence = 0;

function show(view) {
  views.forEach((item) => item.classList.toggle("hidden", item !== view));
  demoShowcase.classList.toggle("hidden", view !== $("#welcome"));
}

function gameIdFromUrl() {
  return new URLSearchParams(window.location.search).get("game");
}

function saveIdentity(value) {
  identity = value;
  sessionStorage.setItem(sessionKey(value.gameId), JSON.stringify(value));
}

function loadIdentity(gameId) {
  try { return JSON.parse(sessionStorage.getItem(sessionKey(gameId))); }
  catch { return null; }
}

/**
 * Calls the public HTTP API and records visible request telemetry.
 * The reference client deliberately has no direct access to GameEngine.
 */
async function api(path, options = {}, silent = false) {
  const method = options.method || "GET";
  const started = performance.now();
  const response = await fetch(path, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const elapsed = Math.round(performance.now() - started);
  if (!silent) addActivity(method, path, response.status, elapsed);
  const body = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const detail = typeof body?.detail === "string" ? body.detail : body?.detail?.message;
    throw new Error(detail || `Request failed with status ${response.status}`);
  }
  return body;
}

function addActivity(method, path, status, elapsed) {
  const list = $("#activity");
  if (!list) return;
  const node = $("#activity-template").content.cloneNode(true);
  node.querySelector(".method").textContent = method;
  node.querySelector("code").textContent = path.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, ":gameId");
  node.querySelector("small").textContent = `${status} · ${elapsed} ms`;
  list.prepend(node);
  while (list.children.length > 7) list.lastElementChild.remove();
}

function inviteUrl(gameId) {
  const url = new URL(window.location.origin);
  url.searchParams.set("game", gameId);
  return url.toString();
}

function makeId(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

/** Creates a lobby and joins every participant through the public player API. */
async function createGame(name, mode) {
  const game = await api("/v1/games", {
    method: "POST",
    body: JSON.stringify({ template_id: "tic-tac-toe", name: `${name}’s match` }),
  });
  const joined = await api(`/v1/games/${game.id}/players`, {
    method: "POST", body: JSON.stringify({ name }),
  });
  const humanId = joined.players[0].id;
  const newIdentity = { gameId: game.id, playerId: humanId, mode, host: true };

  if (mode === "computer") {
    const withBot = await api(`/v1/games/${game.id}/players`, {
      method: "POST", body: JSON.stringify({ name: "Computer" }),
    });
    newIdentity.botId = withBot.players[1].id;
    gameState = (await sendCommand(withBot, "start_game", humanId, {}, "start-computer")).state;
  } else {
    gameState = joined;
  }

  saveIdentity(newIdentity);
  history.replaceState({}, "", `/?game=${game.id}`);
  enterGame();
}

async function joinGame(gameId, name) {
  const state = await api(`/v1/games/${gameId}/players`, {
    method: "POST", body: JSON.stringify({ name }),
  });
  const player = state.players[state.players.length - 1];
  gameState = state;
  saveIdentity({ gameId, playerId: player.id, mode: "friend", host: false });
  enterGame();
}

/**
 * Builds the shared optimistic-concurrency and idempotency command envelope.
 * @returns the authoritative snapshot and events returned by the server.
 */
async function sendCommand(state, type, actorId, payload, key = makeId(type)) {
  return api(`/v1/games/${state.id}/commands`, {
    method: "POST",
    body: JSON.stringify({
      type,
      actor_id: actorId,
      payload,
      expected_version: state.version,
      idempotency_key: key,
    }),
  });
}

function enterGame() {
  show($("#game"));
  $("#invite-box").classList.toggle("hidden", identity.mode !== "friend" || gameState.players.length > 1);
  $("#copy-invite").classList.toggle("hidden", identity.mode !== "friend");
  $("#invite-url").textContent = inviteUrl(identity.gameId);
  render();
  startPolling();
}

function render() {
  if (!gameState) return;
  $("#game-title").textContent = gameState.name;
  $("#version").textContent = gameState.version;
  const piecesBySpace = Object.values(gameState.board.pieces || {}).reduce((map, piece) => {
    map[piece.location] = piece;
    return map;
  }, {});
  const myTurn = gameState.status === "ACTIVE" && gameState.current_player_id === identity.playerId;
  const winner = gameState.board.values?.winner;
  const winningSpaces = winningLine(piecesBySpace);

  const board = $("#board");
  board.innerHTML = "";
  for (let row = 0; row < 3; row += 1) {
    for (let column = 0; column < 3; column += 1) {
      const space = `${row}-${column}`;
      const piece = piecesBySpace[space];
      const cell = document.createElement("button");
      cell.type = "button";
      cell.className = `cell${piece?.kind === "O" ? " mark-o" : ""}${winningSpaces.includes(space) ? " winning" : ""}`;
      cell.textContent = piece?.kind || "";
      cell.setAttribute("role", "gridcell");
      cell.setAttribute("aria-label", piece ? `${space}, ${piece.kind}` : `${space}, empty`);
      cell.disabled = !myTurn || Boolean(piece);
      cell.addEventListener("click", () => playSpace(space));
      board.appendChild(cell);
    }
  }

  $("#players").innerHTML = gameState.players.map((player) => {
    const mark = player.seat === 0 ? "X" : "O";
    const isCurrent = player.id === gameState.current_player_id;
    const tag = player.id === identity.playerId ? "You" : player.id === identity.botId ? "API bot" : `Seat ${player.seat + 1}`;
    return `<div class="player-row ${isCurrent ? "active" : ""}">
      <span class="player-mark">${mark}</span>
      <span><span class="player-name">${escapeHtml(player.name)}</span><span class="player-tag">${tag}</span></span>
      <span class="turn-dot" title="Current turn"></span>
    </div>`;
  }).join("");

  let message;
  if (gameState.status === "LOBBY") message = "Waiting for an opponent to join";
  else if (gameState.status === "FINISHED" && gameState.board.values?.draw) message = "A perfectly balanced draw";
  else if (gameState.status === "FINISHED") message = winner === identity.playerId ? "You won the match" : `${playerName(winner)} won the match`;
  else if (myTurn) message = "Your move";
  else message = `${playerName(gameState.current_player_id)} is thinking`;
  $("#status-message").textContent = message;
  $("#board-hint").textContent = gameState.status === "ACTIVE"
    ? (myTurn ? "Choose any open square" : "The board updates through the API")
    : message;
  $("#invite-box").classList.toggle("hidden", identity.mode !== "friend" || gameState.players.length > 1);
}

function playerName(id) {
  return gameState.players.find((player) => player.id === id)?.name || "Opponent";
}

function escapeHtml(value) {
  const element = document.createElement("span");
  element.textContent = value;
  return element.innerHTML;
}

async function playSpace(spaceId) {
  if (gameState.current_player_id !== identity.playerId) return;
  disableBoard();
  try {
    const result = await sendCommand(gameState, "place_piece", identity.playerId, { space_id: spaceId });
    applyResult(result);
    await maybePlayBot();
  } catch (error) {
    await refreshState();
    $("#board-hint").textContent = error.message;
  }
}

function disableBoard() {
  document.querySelectorAll(".cell").forEach((cell) => { cell.disabled = true; });
}

function applyResult(result) {
  gameState = result.state;
  for (const event of result.events) {
    lastEventSequence = Math.max(lastEventSequence, event.sequence);
    $("#last-event").textContent = event.type.replaceAll("_", " ");
  }
  render();
}

async function refreshState() {
  try {
    gameState = await api(`/v1/games/${identity.gameId}`, {}, true);
    if (identity.host && identity.mode === "friend" && gameState.status === "LOBBY" && gameState.players.length === 2) {
      const result = await sendCommand(gameState, "start_game", identity.playerId, {}, `start-${gameState.id}`);
      applyResult(result);
    } else {
      render();
    }
    await maybePlayBot();
  } catch (error) {
    $("#board-hint").textContent = error.message;
  }
}

function startPolling() {
  clearInterval(pollTimer);
  refreshState();
  pollTimer = setInterval(refreshState, 900);
}

/** Lets the computer act only by submitting the same API command as a human. */
async function maybePlayBot() {
  if (!identity.botId || botThinking || gameState.status !== "ACTIVE" || gameState.current_player_id !== identity.botId) return;
  botThinking = true;
  render();
  await new Promise((resolve) => setTimeout(resolve, 500));
  try {
    const spaceId = chooseBotMove(gameState);
    const result = await sendCommand(gameState, "place_piece", identity.botId, { space_id: spaceId }, `bot-${gameState.version}`);
    applyResult(result);
  } catch {
    await refreshState();
  } finally {
    botThinking = false;
  }
}

/** Chooses a legal move with minimax without mutating the authoritative state. */
function chooseBotMove(state) {
  const board = Array(9).fill(null);
  Object.values(state.board.pieces || {}).forEach((piece) => {
    const [row, column] = piece.location.split("-").map(Number);
    board[row * 3 + column] = piece.kind;
  });
  let bestScore = -Infinity;
  let bestIndex = board.findIndex((value) => value === null);
  board.forEach((value, index) => {
    if (value !== null) return;
    board[index] = "O";
    const score = minimax(board, false);
    board[index] = null;
    if (score > bestScore) { bestScore = score; bestIndex = index; }
  });
  return `${Math.floor(bestIndex / 3)}-${bestIndex % 3}`;
}

function minimax(board, maximizing) {
  const outcome = boardWinner(board);
  if (outcome === "O") return 10;
  if (outcome === "X") return -10;
  if (board.every(Boolean)) return 0;
  const scores = [];
  board.forEach((value, index) => {
    if (value !== null) return;
    board[index] = maximizing ? "O" : "X";
    scores.push(minimax(board, !maximizing));
    board[index] = null;
  });
  return maximizing ? Math.max(...scores) : Math.min(...scores);
}

const lines = [[0,1,2],[3,4,5],[6,7,8],[0,3,6],[1,4,7],[2,5,8],[0,4,8],[2,4,6]];
function boardWinner(board) {
  for (const [a, b, c] of lines) if (board[a] && board[a] === board[b] && board[a] === board[c]) return board[a];
  return null;
}

function winningLine(piecesBySpace) {
  const spaces = ["0-0","0-1","0-2","1-0","1-1","1-2","2-0","2-1","2-2"];
  const board = spaces.map((space) => piecesBySpace[space]?.kind || null);
  const line = lines.find(([a,b,c]) => board[a] && board[a] === board[b] && board[a] === board[c]);
  return line ? line.map((index) => spaces[index]) : [];
}

$("#start-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.currentTarget.querySelector("button");
  const name = $("#player-name").value.trim();
  const mode = new FormData(event.currentTarget).get("mode");
  button.disabled = true;
  $("#start-error").textContent = "";
  try { await createGame(name, mode); }
  catch (error) { $("#start-error").textContent = error.message; }
  finally { button.disabled = false; }
});

$("#join-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.currentTarget.querySelector("button");
  button.disabled = true;
  $("#join-error").textContent = "";
  try { await joinGame(gameIdFromUrl(), $("#join-name").value.trim()); }
  catch (error) { $("#join-error").textContent = error.message; }
  finally { button.disabled = false; }
});

$("#copy-invite").addEventListener("click", async () => {
  await navigator.clipboard.writeText(inviteUrl(identity.gameId));
  $("#copy-invite").textContent = "Link copied";
  setTimeout(() => { $("#copy-invite").textContent = "Copy invite link"; }, 1600);
});

$("#new-game").addEventListener("click", () => {
  clearInterval(pollTimer);
  history.replaceState({}, "", "/");
  gameState = null;
  identity = null;
  show($("#welcome"));
});

async function bootstrap() {
  const gameId = gameIdFromUrl();
  if (!gameId) { show($("#welcome")); return; }
  identity = loadIdentity(gameId);
  try {
    gameState = await api(`/v1/games/${gameId}`);
    if (identity) {
      enterGame();
    } else if (gameState.status === "LOBBY" && gameState.players.length < 2) {
      $("#join-game-name").textContent = `Join “${gameState.name}” and play through the public game API.`;
      show($("#join"));
    } else {
      history.replaceState({}, "", "/");
      show($("#welcome"));
      $("#start-error").textContent = "That game no longer has an open seat.";
    }
  } catch {
    history.replaceState({}, "", "/");
    show($("#welcome"));
    $("#start-error").textContent = "That game could not be found. It may have expired after a restart.";
  }
}

bootstrap();
