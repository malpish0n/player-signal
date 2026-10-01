# Local Stripe sandbox and Ollama

The local setup accepts **test keys only**. Live payments are intentionally disabled. The owner supplied Indie and Studio Stripe price IDs; the API verifies active monthly prices and displays the actual amounts/currency (currently 99 PLN and 299 PLN). Price IDs and secrets are held in the ignored `.env`, never in Git.

## Start and configure

Use all overlays when operating the local stack:

```sh
docker compose --env-file .env -f infra/docker-compose.yml -f infra/local-models.compose.yml -f infra/stripe-local.compose.yml --profile local-ai --profile billing up -d
```

Ollama has cloud disabled and no host port. Classification uses `qwen3:4b` (local model ID `359d7dd4bcda`), `think:false`, deterministic temperature, structured JSON and exact-source quote validation. Embeddings use `embeddinggemma` (`85462619ee72`). The runtime images are pinned by digest. Do not repull mutable model tags silently; updating a model requires a new analysis identity / embedding revision so old results are not mistaken for new ones. Local batch size is 3 reviews on this CPU-only Docker runtime. These are application usage quotas, not fees charged by Ollama. Local compute still uses the machine's RAM, CPU and electricity.

For a fresh install, pull each model in the private service with `docker compose ... exec ollama ollama pull qwen3:4b` and `... pull embeddinggemma`, then set ANALYSIS_PROVIDER=ollama, ANALYSIS_MODEL=qwen3:4b, EMBEDDING_PROVIDER=ollama, EMBEDDING_MODEL=embeddinggemma and ALLOW_PAID_AI=false. Without the local-model overlay, configure OLLAMA_URL for your installed local service.

## Stripe prerequisites

Set STRIPE_ENABLED=true, STRIPE_SECRET_KEY to a sandbox restricted key, STRIPE_PRICE_INDIE_MONTHLY and STRIPE_PRICE_STUDIO_MONTHLY to the matching sandbox recurring prices, and APP_PUBLIC_URL=http://localhost:3000. Required restricted-key capabilities: Prices read, Customers read/write, Checkout Sessions read/write, Subscriptions read, Billing Portal sessions write, and **Debugging Tools write** for the local CLI listener. Configure a test Customer Portal in Stripe if it has not been saved yet. Use the exact permission names presented by the dashboard; do not broaden unrelated capabilities.

The listener logs its `whsec_` signing secret. Copy it privately into STRIPE_WEBHOOK_SECRET in `.env`, then recreate the backend. Never commit or paste that secret into chat. A CLI forwarding secret differs from a dashboard endpoint's signing secret. The signed raw-body endpoint is `/api/billing/webhook` on backend port 8080; the local listener reaches it on the private Docker network. No public webhook URL or tunnel is needed for local operation.

Open `/settings`, choose a plan, complete **sandbox** Checkout with Stripe test data, then refresh payment status. A browser return URL never grants access. Subscription access comes from a server-side Stripe reconciliation. The portal controls existing subscriptions; a second checkout is refused while a subscription exists. Uncertain checkout retries reuse a persisted idempotency key. Webhook signatures have a five-minute tolerance; event IDs deduplicate delivery, current Stripe state handles out-of-order events, and failed reconciliations retry with a bounded attempt count. Background subscription refresh runs every five minutes. The API version is pinned to 2024-12-18.acacia.

Sandbox quotas: Free 1 game / 500 monthly attempts / 10 reports per game / 1 seat; Indie 3 / 5,000 / 50 / 3; Studio 10 / 20,000 / 100 / 10. Existing data is retained after downgrade, and reads of historical data remain available. History-day and feature paywalls from the indicative blueprint are not enabled in this local sandbox. These quota values are initial implementation defaults; do not advertise them as a finalized commercial offer. Past-due access has at most three days of grace, only for a previously active paid plan. Stale verification (48 hours) does not grant paid access. Account deletion refuses active subscriptions and pending checkouts.

## Remaining public-launch dependencies

Local sandbox wiring is not a live launch. Hosting/domain, live billing enablement and prices, legal pages, account recovery/email delivery, full authorization/lifecycle acceptance and 3–5-game model quality evaluation remain separate release work. The production Compose template does not yet enable Stripe or the local listener. No paid OpenAI fallback is used.
