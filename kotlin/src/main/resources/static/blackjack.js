const $ = (selector) => document.querySelector(selector);
let state = null;
let playerId = null;

async function api(path, options = {}) {
  const started = performance.now();
  const response = await fetch(path, { ...options, headers: { "Content-Type": "application/json", ...(options.headers || {}) } });
  const body = await response.json();
  addActivity(options.method || "GET", path, response.status, Math.round(performance.now() - started));
  if (!response.ok) throw new Error(typeof body.detail === "string" ? body.detail : body.detail?.message || "Request failed");
  return body;
}

function addActivity(method, path, status, elapsed) {
  const item = document.createElement("li");
  item.innerHTML = `<span class="method">${method}</span><div><code>${path.replace(/[0-9a-f-]{36}/gi, ":gameId")}</code><small>${status} · ${elapsed} ms</small></div>`;
  $("#blackjack-activity").prepend(item);
  while ($("#blackjack-activity").children.length > 7) $("#blackjack-activity").lastElementChild.remove();
}

async function command(type) {
  const result = await api(`/v1/games/${state.id}/commands`, {
    method: "POST",
    body: JSON.stringify({ type, actor_id: playerId, payload: {}, expected_version: state.version, idempotency_key: `${type}-${crypto.randomUUID()}` }),
  });
  state = result.state;
  render();
}

async function start(name) {
  const game = await api("/v1/games", { method: "POST", body: JSON.stringify({ template_id: "blackjack", name: `${name} at the table` }) });
  const joined = await api(`/v1/games/${game.id}/players`, { method: "POST", body: JSON.stringify({ name }) });
  playerId = joined.players[0].id;
  state = joined;
  await command("start_game");
  $("#blackjack-welcome").classList.add("hidden");
  $("#blackjack-game").classList.remove("hidden");
  $("#player-label").textContent = name;
}

function cardMarkup(card, hidden = false) {
  if (hidden) return '<div class="playing-card back">Hidden</div>';
  const symbols = { clubs: "♣", diamonds: "♦", hearts: "♥", spades: "♠" };
  const red = card.suit === "diamonds" || card.suit === "hearts";
  return `<div class="playing-card ${red ? "red" : ""}">${card.rank}<small>${symbols[card.suit]}</small></div>`;
}

function render() {
  const deck = state.board.decks.shoe;
  const player = deck.hands[playerId] || [];
  const dealer = deck.hands.dealer || [];
  const revealed = Boolean(state.board.values.dealer_revealed);
  $("#player-hand").innerHTML = player.map((card) => cardMarkup(card)).join("");
  $("#dealer-hand").innerHTML = dealer.map((card, index) => cardMarkup(card, !revealed && index === 1)).join("");
  $("#player-total").textContent = state.board.values.player_total;
  $("#dealer-total").textContent = revealed ? state.board.values.dealer_total : `${state.board.values.dealer_total}+`;
  $("#blackjack-version").textContent = state.version;
  $("#cards-remaining").textContent = deck.draw_pile.length;
  const active = state.status === "ACTIVE";
  $("#hit").disabled = !active;
  $("#stand").disabled = !active;
  const messages = { PLAYER_WIN: "You beat the dealer", DEALER_WIN: "The dealer takes the hand", PUSH: "Push — bets return" };
  $("#blackjack-message").textContent = active ? "Hit or stand? Every choice is an API command." : messages[state.board.values.outcome];
}

$("#blackjack-start").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("#blackjack-error").textContent = "";
  try { await start($("#blackjack-name").value.trim()); }
  catch (error) { $("#blackjack-error").textContent = error.message; }
});
$("#hit").addEventListener("click", () => command("hit").catch((error) => { $("#blackjack-message").textContent = error.message; }));
$("#stand").addEventListener("click", () => command("stand").catch((error) => { $("#blackjack-message").textContent = error.message; }));
$("#blackjack-new").addEventListener("click", () => location.reload());
