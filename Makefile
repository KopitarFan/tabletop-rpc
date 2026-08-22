.PHONY: run test

run:
	cd kotlin && ./mvnw spring-boot:run

test:
	cd kotlin && ./mvnw test
