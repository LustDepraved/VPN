run-control:
	PYTHONPATH=apps/control-plane uvicorn app.main:app --reload --port 8000

test-control:
	PYTHONPATH=apps/control-plane pytest -q tests/test_control_plane.py

run-control-docker:
	docker compose -f infra/docker-compose.yml up --build control-plane

build-android-apk:
	cd apps/mobile-android && ./gradlew assembleDebug

setup-node:
	bash scripts/setup_wireguard_node.sh
