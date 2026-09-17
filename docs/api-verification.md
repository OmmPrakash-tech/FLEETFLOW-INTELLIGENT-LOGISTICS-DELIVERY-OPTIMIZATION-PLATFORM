# HTTP endpoint verification

Executed 17 September 2026 by HttpAcceptanceTests: **37 supported API method/path pairs**, all test assertions passed. Observed 4xx statuses below are expected negative checks. This table does not claim exhaustive coverage of every endpoint branch. OpenAPI, unknown-path/method and body-limit probes are listed separately in verification.md.

| Method and route | Observed expected statuses |
|---|---|
| `DELETE /api/addresses/{id}` | [200, 404] |
| `GET /api/addresses` | [200] |
| `GET /api/alerts` | [200] |
| `GET /api/analytics` | [200, 403] |
| `GET /api/audit` | [200, 403] |
| `GET /api/auth/me` | [401] |
| `GET /api/drivers` | [200] |
| `GET /api/inventory` | [200] |
| `GET /api/orders` | [200] |
| `GET /api/orders/{id}` | [200, 403, 404] |
| `GET /api/products` | [200] |
| `GET /api/routes` | [200] |
| `GET /api/shipments` | [200] |
| `GET /api/tracking/{id}` | [200, 403] |
| `GET /api/tracking/{id}/stream` | [200] |
| `GET /api/users` | [200, 403] |
| `GET /api/vehicles` | [200] |
| `GET /api/warehouses` | [200] |
| `PATCH /api/drivers/{id}/availability` | [200, 403, 409] |
| `PATCH /api/users/{id}` | [200, 403, 409] |
| `PATCH /api/vehicles/{id}/availability` | [200, 409] |
| `POST /api/addresses` | [200] |
| `POST /api/alerts/{id}/resolve` | [404] |
| `POST /api/auth/login` | [200, 401] |
| `POST /api/auth/logout` | [200] |
| `POST /api/auth/register` | [200] |
| `POST /api/drivers` | [200] |
| `POST /api/inventory/stock` | [200] |
| `POST /api/orders` | [200, 400] |
| `POST /api/orders/{id}/assign` | [200] |
| `POST /api/orders/{id}/transition` | [200, 403, 409] |
| `POST /api/products` | [200, 403] |
| `POST /api/routes/plan` | [200, 400] |
| `POST /api/routes/shortest-path` | [200, 400] |
| `POST /api/tracking/drivers/{id}/position` | [200, 403] |
| `POST /api/vehicles` | [200] |
| `POST /api/warehouses` | [200] |
