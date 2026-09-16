import {
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type ReactNode,
} from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
const OrderChart = lazy(() => import("./OrderChart"));
import {
  ArrowDownLeft,
  ArrowUpRight,
  Box,
  Check,
  CheckCheck,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  MapPin,
  Package,
  Plus,
  Radio,
  RefreshCw,
  Search,
  SlidersHorizontal,
  Truck,
  Warehouse,
  X,
  AlertTriangle,
} from "lucide-react";
import {
  api,
  post,
  getSession,
  setSession,
  date,
  label,
  money,
  type Row,
} from "./api";

export function Heading({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <span className="eyebrow">{eyebrow || "OPERATIONS WORKSPACE"}</span>
        <h1>
          {title}
          <span className="heading-dot">.</span>
        </h1>
        <p>{description}</p>
      </div>
      <div className="heading-actions">{children}</div>
    </div>
  );
}
function Loading({ error }: { error?: Error | null }) {
  return error ? (
    <div className="error" role="alert">
      {error.message}
    </div>
  ) : (
    <div className="loading">
      <RefreshCw className="spin" size={20} /> Loading live records…
    </div>
  );
}
function Empty({
  text = "No records yet. Your activity will appear here.",
}: {
  text?: string;
}) {
  return (
    <div className="empty">
      <Package size={30} />
      <b>Nothing here yet</b>
      <p>{text}</p>
    </div>
  );
}
function Badge({ value }: { value: string }) {
  return (
    <span className={`badge ${value?.toLowerCase()}`}>
      <i />
      {label(value)}
    </span>
  );
}
const titleCase = (s: string) => s.split("_").map(label).join(" ");
function Metric({
  title,
  value,
  detail,
  icon: Icon,
  featured = false,
}: {
  title: string;
  value: string | number;
  detail: string;
  icon: any;
  featured?: boolean;
}) {
  return (
    <article className={`metric ${featured ? "featured" : ""}`}>
      <div>
        <span>{title}</span>
        <Icon size={19} />
      </div>
      <strong>{value}</strong>
      <small>
        {featured ? (
          <span className="pulse" />
        ) : (
          <span className="metric-dash" />
        )}
        {detail}
      </small>
    </article>
  );
}
export function Dashboard({ staff }: { staff: boolean }) {
  const q = useQuery({
    queryKey: ["analytics"],
    queryFn: () => api("/api/analytics"),
  });
  const recent = useQuery({
    queryKey: ["recent-orders"],
    queryFn: () => api<Row[]>("/api/orders?size=5"),
  });
  if (!q.data) return <Loading error={q.error} />;
  const {
    summary,
    byStatus,
    trends,
    warehouses = [],
    drivers = [],
    alerts = [],
  } = q.data;
  const rate = summary.orders
    ? Math.round((summary.delivered / summary.orders) * 100)
    : 0;
  return (
    <>
      <Heading
        eyebrow={staff ? "NETWORK COMMAND CENTER" : "YOUR DELIVERY WORKSPACE"}
        title={staff ? "Operations overview" : "Your deliveries"}
        description={
          staff
            ? "A clear view of every movement across your network."
            : "From dispatch to doorstep. Stay close to every delivery."
        }
      >
        <button className="secondary" onClick={() => q.refetch()}>
          <RefreshCw size={15} />
          Refresh
        </button>
        <Link className="primary" to="/orders">
          <Plus size={17} />
          New order
        </Link>
      </Heading>
      <div className="overview-strip">
        <span>
          <span className="pulse" /> LIVE OPERATIONS
        </span>
        <p>
          All figures are calculated from persisted orders and delivery events.
        </p>
        <span className="strip-id">NETWORK / 01</span>
      </div>
      <section className="metrics">
        <Metric
          title="Total orders"
          value={summary.orders}
          detail="All recorded orders"
          icon={Package}
        />
        <Metric
          title="Active deliveries"
          value={summary.active}
          detail="Moving through the network"
          icon={Truck}
          featured
        />
        <Metric
          title="Delivered"
          value={summary.delivered}
          detail={`${rate}% of all orders`}
          icon={CheckCheck}
        />
        <Metric
          title="Beyond SLA"
          value={summary.delayed}
          detail="Orders exceeding their promise"
          icon={Clock}
        />
      </section>
      <section className="dashboard-grid">
        <article className="panel trend-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">ORDER ACTIVITY</span>
              <h2>Your network, in motion</h2>
            </div>
            <span className="subtle-tag">Last 30 days</span>
          </div>
          <div className="chart-legend">
            <span>
              <i />
              Orders created
            </span>
            <span>
              <i className="sage" />
              Delivered from cohort
            </span>
          </div>
          {trends.length ? (
            <div className="chart">
              <Suspense fallback={<Loading />}>
                <OrderChart data={trends} />
              </Suspense>
            </div>
          ) : (
            <Empty text="Create your first order to begin the activity chart." />
          )}
        </article>
        <article className="panel status-panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">DELIVERY HEALTH</span>
              <h2>Every stage accounted for</h2>
            </div>
            <ArrowUpRight size={20} />
          </div>
          <div className="completion">
            <div
              className="progress-ring"
              style={{
                background: `conic-gradient(#2b745b ${rate}%, #e8eee8 0)`,
              }}
            >
              <div>
                <strong>
                  {rate}
                  <small>%</small>
                </strong>
                <span>delivered</span>
              </div>
            </div>
            <div>
              <b>{summary.delivered} completed</b>
              <p>{summary.active} in progress</p>
              <small>
                {Number(summary.average_delivery_hours).toFixed(1)}h average
                delivery
              </small>
            </div>
          </div>
          <div className="status-list">
            {byStatus.length ? (
              byStatus.map((s: Row) => (
                <div key={s.status}>
                  <Badge value={s.status} />
                  <b>{s.count}</b>
                </div>
              ))
            ) : (
              <p className="muted">No orders recorded.</p>
            )}
          </div>
        </article>
      </section>
      <section className="dashboard-grid lower">
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">LATEST MOVEMENTS</span>
              <h2>Recent orders</h2>
            </div>
            <Link className="text-link" to="/orders">
              View all <ArrowUpRight size={15} />
            </Link>
          </div>
          {recent.data?.length ? (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Order</th>
                    <th>Destination</th>
                    <th>Status</th>
                    <th>Value</th>
                  </tr>
                </thead>
                <tbody>
                  {recent.data.map((o) => (
                    <tr key={o.id}>
                      <td>
                        <Link className="order-link" to={`/orders/${o.id}`}>
                          <span className="table-icon">
                            <Package size={15} />
                          </span>
                          FF-{o.id.slice(0, 8).toUpperCase()}
                        </Link>
                      </td>
                      <td>{o.address}</td>
                      <td>
                        <Badge value={o.status} />
                      </td>
                      <td>{money(o.total)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <Empty />
          )}
        </article>
        <article className="panel">
          <div className="panel-heading">
            <div>
              <span className="eyebrow">
                {staff ? "WAREHOUSE CAPACITY" : "ORDER SUMMARY"}
              </span>
              <h2>{staff ? "Room to keep moving" : "Your activity"}</h2>
            </div>
            <Warehouse size={20} />
          </div>
          {staff ? (
            <div className="warehouse-loads">
              {warehouses.length ? (
                warehouses.map((w: Row) => (
                  <div key={w.name}>
                    <div>
                      <b>{w.name}</b>
                      <span>{w.utilization}%</span>
                    </div>
                    <div className="load-track">
                      <i style={{ width: `${w.utilization}%` }} />
                    </div>
                    <small>
                      {w.current_load} active orders / {w.capacity} slots
                    </small>
                  </div>
                ))
              ) : (
                <Empty />
              )}
            </div>
          ) : (
            <div className="customer-summary">
              <strong>{money(summary.order_value)}</strong>
              <p>Total recorded order value</p>
              <Link to="/tracking" className="secondary">
                Track a shipment <ArrowUpRight size={16} />
              </Link>
            </div>
          )}
          {staff && (
            <div className="fleet-summary">
              <Truck size={18} />
              <span>
                <b>
                  {drivers.find((d: Row) => d.status === "AVAILABLE")?.count ||
                    0}{" "}
                  drivers available
                </b>
                <small>Ready for the next assignment</small>
              </span>
              <Link to="/drivers">
                <ArrowUpRight size={19} />
              </Link>
            </div>
          )}
        </article>
      </section>
      {staff && alerts.length > 0 && (
        <div className="alert-strip">
          <AlertTriangle size={20} />
          <div>
            <b>{alerts.length} recent alerts need attention</b>
            <p>{alerts[0].message}</p>
          </div>
          <Link to="/alerts">
            Review alerts <ArrowUpRight size={16} />
          </Link>
        </div>
      )}
    </>
  );
}

type Field = {
  name: string;
  label: string;
  type?: string;
  options?: { value: string; label: string }[];
  required?: boolean;
  value?: string | number;
  min?: number;
  max?: number;
};
function Dialog({
  title,
  fields,
  submit,
  close,
  extra,
}: {
  title: string;
  fields: Field[];
  submit: (data: Row) => Promise<void>;
  close: () => void;
  extra?: ReactNode;
}) {
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const first = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement;
    first.current?.querySelector<HTMLInputElement>("input,select")?.focus();
    return () => previous?.focus();
  }, []);
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(e.currentTarget),
      data: Row = {};
    for (const field of fields) {
      const v = form.get(field.name);
      data[field.name] = field.type === "number" ? Number(v) : v;
    }
    try {
      await submit(data);
      close();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget && !busy) close();
      }}
    >
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
        ref={first}
        onKeyDown={(e) => {
          if (e.key === "Escape" && !busy) close();
          if (e.key === "Tab") {
            const els = Array.from(
              e.currentTarget.querySelectorAll<HTMLElement>(
                "button:not(:disabled),input,select",
              ),
            );
            const first = els[0],
              last = els[els.length - 1];
            if (e.shiftKey && document.activeElement === first) {
              e.preventDefault();
              last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first.focus();
            }
          }
        }}
      >
        <div className="modal-head">
          <div>
            <span className="eyebrow">FLEETFLOW OPERATIONS</span>
            <h2>{title}</h2>
          </div>
          <button
            aria-label="Close"
            className="icon-button"
            onClick={close}
            disabled={busy}
          >
            <X size={20} />
          </button>
        </div>
        <form onSubmit={save}>
          <div className="form-grid">
            {fields.map((f) => (
              <label key={f.name}>
                {f.label}
                {f.options ? (
                  <select
                    required={f.required !== false}
                    name={f.name}
                    defaultValue={f.value ?? ""}
                  >
                    <option value="">Select…</option>
                    {f.options.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    required={f.required !== false}
                    name={f.name}
                    type={f.type || "text"}
                    defaultValue={f.value}
                    min={f.min}
                    max={f.max}
                    step={f.type === "number" ? "any" : undefined}
                  />
                )}
              </label>
            ))}
          </div>
          {extra}
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          <div className="modal-actions">
            <button
              type="button"
              className="secondary"
              onClick={close}
              disabled={busy}
            >
              Cancel
            </button>
            <button className="primary" disabled={busy}>
              {busy ? "Saving…" : "Save"}
              <Check size={16} />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
const selectOptions = (rows: Row[] | undefined, key = "name") =>
  (rows || []).map((r) => ({ value: r.id, label: r[key] }));
function Pagination({
  page,
  setPage,
  count,
}: {
  page: number;
  setPage: (n: number) => void;
  count: number;
}) {
  return (
    <div className="pagination">
      <span>
        Page {page + 1} · {count} records
      </span>
      <div>
        <button
          className="icon-button"
          aria-label="Previous page"
          disabled={page === 0}
          onClick={() => setPage(page - 1)}
        >
          <ChevronLeft size={17} />
        </button>
        <button
          className="icon-button"
          aria-label="Next page"
          disabled={count < 25}
          onClick={() => setPage(page + 1)}
        >
          <ChevronRight size={17} />
        </button>
      </div>
    </div>
  );
}

export function Orders({
  staff,
  history = false,
}: {
  staff: boolean;
  history?: boolean;
}) {
  const [search, setSearch] = useState(""),
    [status, setStatus] = useState(history ? "DELIVERED" : ""),
    [sort, setSort] = useState("newest"),
    [page, setPage] = useState(0),
    [adding, setAdding] = useState(false);
  const creationKey = useRef(crypto.randomUUID());
  const [items, setItems] = useState([{ productId: "", quantity: 1 }]);
  const client = useQueryClient();
  const navigate = useNavigate();
  const q = useQuery({
    queryKey: ["orders", search, status, sort, page],
    queryFn: () =>
      api<Row[]>(
        `/api/orders?search=${encodeURIComponent(search)}&status=${status}&sort=${sort}&page=${page}`,
      ),
  });
  const products = useQuery({
    queryKey: ["products-select"],
    queryFn: () => api<Row[]>("/api/products?size=100"),
    enabled: adding,
  });
  const driver = getSession()?.user.role === "DRIVER";
  const fields: Field[] = [
    { name: "address", label: "Delivery address" },
    { name: "latitude", label: "Latitude", type: "number", min: -90, max: 90 },
    {
      name: "longitude",
      label: "Longitude",
      type: "number",
      min: -180,
      max: 180,
    },
    {
      name: "priority",
      label: "Priority",
      options: [
        { value: "1", label: "Standard" },
        { value: "2", label: "Priority" },
        { value: "3", label: "Urgent" },
      ],
      value: "1",
    },
    {
      name: "slaHours",
      label: "Delivery promise (hours)",
      type: "number",
      min: 1,
      max: 168,
      value: 24,
    },
  ];
  return (
    <>
      <Heading
        title={
          staff ? "Order management" : driver ? "Your assignments" : "My orders"
        }
        description="From the first request to the final handoff. Every order in one place."
      >
        {!driver && (
          <button
            className="primary"
            onClick={() => {
              creationKey.current = crypto.randomUUID();
              setItems([{ productId: "", quantity: 1 }]);
              setAdding(true);
            }}
          >
            <Plus size={17} />
            Create order
          </button>
        )}
      </Heading>
      <div className="panel">
        <div className="toolbar">
          <div className="search-field">
            <Search size={17} />
            <input
              aria-label="Search orders"
              placeholder="Search order ID or destination…"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
            />
          </div>
          <div className="filters">
            <SlidersHorizontal size={17} />
            <select
              aria-label="Order status"
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              {[
                "WAREHOUSE_ASSIGNED",
                "PICKING",
                "PACKED",
                "DISPATCHED",
                "IN_TRANSIT",
                "OUT_FOR_DELIVERY",
                "DELIVERED",
                "CANCELLED",
                "FAILED",
                "RETURN_REQUESTED",
                "RETURNED",
              ].map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
            <select
              aria-label="Sort orders"
              value={sort}
              onChange={(e) => setSort(e.target.value)}
            >
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="priority">Priority first</option>
            </select>
          </div>
        </div>
        {q.isPending || q.error ? (
          <Loading error={q.error} />
        ) : !q.data?.length ? (
          <Empty />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Order ID / created</th>
                  <th>Destination</th>
                  <th>Warehouse</th>
                  <th>Priority</th>
                  <th>Status</th>
                  <th>Value</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <Link className="order-link" to={`/orders/${o.id}`}>
                        FF-{o.id.slice(0, 8).toUpperCase()}
                      </Link>
                      <small className="cell-sub">{date(o.created_at)}</small>
                    </td>
                    <td>{o.address}</td>
                    <td>{o.warehouse_name}</td>
                    <td>
                      <span className={`priority priority-${o.priority}`}>
                        {["", "Standard", "Priority", "Urgent"][o.priority]}
                      </span>
                    </td>
                    <td>
                      <Badge value={o.status} />
                    </td>
                    <td>{money(o.total)}</td>
                    <td>
                      <Link
                        className="icon-button"
                        aria-label="View order"
                        to={`/orders/${o.id}`}
                      >
                        <ArrowUpRight size={17} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination page={page} setPage={setPage} count={q.data?.length || 0} />
      </div>
      {adding && (
        <Dialog
          title="Create an order"
          fields={fields}
          close={() => setAdding(false)}
          extra={
            <div className="order-lines">
              <h3>Order items</h3>
              {items.map((item, index) => (
                <div className="order-line" key={index}>
                  <label>
                    Product {index + 1}
                    <select
                      required
                      value={item.productId}
                      onChange={(e) =>
                        setItems(
                          items.map((v, i) =>
                            i === index
                              ? { ...v, productId: e.target.value }
                              : v,
                          ),
                        )
                      }
                    >
                      <option value="">Choose product…</option>
                      {products.data?.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Quantity
                    <input
                      required
                      type="number"
                      min={1}
                      max={100000}
                      step={1}
                      value={item.quantity}
                      onChange={(e) =>
                        setItems(
                          items.map((v, i) =>
                            i === index
                              ? { ...v, quantity: Number(e.target.value) }
                              : v,
                          ),
                        )
                      }
                    />
                  </label>
                  {items.length > 1 && (
                    <button
                      type="button"
                      className="icon-button"
                      aria-label={`Remove item ${index + 1}`}
                      onClick={() =>
                        setItems(items.filter((_, i) => i !== index))
                      }
                    >
                      <X size={16} />
                    </button>
                  )}
                </div>
              ))}
              <button
                type="button"
                className="secondary"
                disabled={items.length >= 100}
                onClick={() =>
                  setItems([...items, { productId: "", quantity: 1 }])
                }
              >
                <Plus size={15} />
                Add another item
              </button>
            </div>
          }
          submit={async (d) => {
            const result = await post(
              "/api/orders",
              {
                address: d.address,
                latitude: d.latitude,
                longitude: d.longitude,
                priority: Number(d.priority),
                slaHours: d.slaHours,
                items,
              },
              creationKey.current,
            );
            await client.invalidateQueries();
            navigate(`/orders/${result.id}`);
          }}
        />
      )}
    </>
  );
}

const transitions: Record<string, string[]> = {
  WAREHOUSE_ASSIGNED: ["PICKING", "CANCELLED"],
  PICKING: ["PACKED", "CANCELLED"],
  PACKED: ["DISPATCHED", "CANCELLED"],
  DISPATCHED: ["IN_TRANSIT", "FAILED"],
  IN_TRANSIT: ["OUT_FOR_DELIVERY", "FAILED"],
  OUT_FOR_DELIVERY: ["DELIVERED", "FAILED"],
  DELIVERED: ["RETURN_REQUESTED"],
  RETURN_REQUESTED: ["RETURNED"],
  FAILED: ["RETURNED"],
};
export function OrderDetail({ staff }: { staff: boolean }) {
  const { id } = useParams();
  const client = useQueryClient();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const q = useQuery({
    queryKey: ["order", id],
    queryFn: () => api(`/api/orders/${id}`),
  });
  if (!q.data) return <Loading error={q.error} />;
  const o = q.data;
  const driver = getSession()?.user.role === "DRIVER";
  async function action(path: string, body?: unknown) {
    setBusy(true);
    setError("");
    try {
      await post(path, body);
      await client.invalidateQueries();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const actions = (transitions[o.status] || []).filter(
    (s) =>
      staff ||
      (driver
        ? ["IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "FAILED"].includes(s)
        : ["CANCELLED", "RETURN_REQUESTED"].includes(s)),
  );
  return (
    <>
      <Link className="text-link back-link" to="/orders">
        <ChevronLeft size={16} />
        All orders
      </Link>
      <Heading
        title={`Order FF-${o.id.slice(0, 8).toUpperCase()}`}
        description={`Created ${date(o.created_at)}`}
      >
        <Badge value={o.status} />
      </Heading>
      {error && <div className="error">{error}</div>}
      <div className="detail-grid">
        <article className="panel padded">
          <span className="eyebrow">DELIVERY DETAILS</span>
          <h2>{o.address}</h2>
          <div className="key-values">
            <div>
              <span>Destination coordinates</span>
              <b>
                {o.latitude}, {o.longitude}
              </b>
            </div>
            <div>
              <span>Delivery promise</span>
              <b>{o.sla_hours} hours</b>
            </div>
            <div>
              <span>Priority</span>
              <b>{["", "Standard", "Priority", "Urgent"][o.priority]}</b>
            </div>
            <div>
              <span>Order value</span>
              <b>{money(o.total)}</b>
            </div>
          </div>
          <h3>Items in this order</h3>
          {o.items.map((item: Row) => (
            <div key={item.product_id} className="order-item">
              <span className="table-icon">
                <Box size={20} />
              </span>
              <div>
                <b>{item.name}</b>
                <small>
                  {item.sku} · Qty {item.quantity}
                </small>
              </div>
              <strong>{money(item.unit_price * item.quantity)}</strong>
            </div>
          ))}
        </article>
        <article className="panel padded">
          <span className="eyebrow">NEXT STEPS</span>
          <h2>Move this order forward</h2>
          <p className="muted">
            Actions are checked against the order’s current state and your
            access.
          </p>
          <div className="action-list">
            {staff && o.status === "PACKED" && !o.shipments.length && (
              <button
                className="primary"
                disabled={busy}
                onClick={() => action(`/api/orders/${id}/assign`)}
              >
                <Truck size={17} />
                Assign best available driver
              </button>
            )}
            {actions.map((s) => (
              <button
                key={s}
                className={
                  s === "CANCELLED" || s === "FAILED"
                    ? "secondary danger"
                    : "secondary"
                }
                disabled={busy || (s === "DISPATCHED" && !o.shipments.length)}
                onClick={() =>
                  action(`/api/orders/${id}/transition`, { status: s })
                }
              >
                {label(s)}
                <ArrowUpRight size={17} />
              </button>
            ))}
          </div>
          {!actions.length && (
            <p className="muted">No actions available in this state.</p>
          )}
          {o.shipments.map((s: Row) => (
            <Link key={s.id} className="tracking-link" to={`/tracking/${s.id}`}>
              <Radio size={18} />
              <span>
                Track this shipment<small>{label(s.status)}</small>
              </span>
              <ArrowUpRight size={20} />
            </Link>
          ))}
        </article>
      </div>
    </>
  );
}

const columns: Record<string, string[]> = {
  warehouses: ["name", "address", "status", "capacity", "current_load"],
  inventory: [
    "product_name",
    "sku",
    "warehouse_name",
    "quantity",
    "reserved",
    "available",
  ],
  products: ["sku", "name", "weight_kg", "price"],
  drivers: [
    "name",
    "status",
    "registration_number",
    "workload",
    "latitude",
    "longitude",
  ],
  vehicles: ["registration_number", "type", "capacity_kg", "status"],
  shipments: ["id", "address", "driver_name", "status", "eta"],
  users: ["name", "email", "role", "active", "force_reset"],
  alerts: ["type", "message", "resolved", "created_at"],
  audit: ["action", "actor_name", "resource_id", "created_at"],
  addresses: ["label", "address", "latitude", "longitude"],
};
export function DataPage({ resource }: { resource: string }) {
  const [page, setPage] = useState(0),
    [adding, setAdding] = useState(false),
    [edit, setEdit] = useState<Row | null>(null),
    [error, setError] = useState("");
  const client = useQueryClient();
  const q = useQuery({
    queryKey: [resource, page],
    queryFn: () => api<Row[]>(`/api/${resource}?page=${page}`),
  });
  const products = useQuery({
    queryKey: ["products-select"],
    queryFn: () => api<Row[]>("/api/products?size=100"),
    enabled: adding && resource === "inventory",
  });
  const warehouses = useQuery({
    queryKey: ["warehouses-select"],
    queryFn: () => api<Row[]>("/api/warehouses?size=100"),
    enabled: adding && resource === "inventory",
  });
  const vehicles = useQuery({
    queryKey: ["vehicles-select"],
    queryFn: () => api<Row[]>("/api/vehicles?size=100"),
    enabled: adding && resource === "drivers",
  });
  const f = (
    name: string,
    labelText: string,
    type = "text",
    value?: string | number,
  ): Field => ({ name, label: labelText, type, value });
  const fields: Record<string, Field[]> = {
    products: [
      f("sku", "SKU"),
      f("name", "Product name"),
      f("weightKg", "Weight (kg)", "number"),
      f("price", "Price (INR)", "number"),
    ],
    warehouses: [
      f("name", "Warehouse name"),
      f("address", "Address"),
      f("latitude", "Latitude", "number"),
      f("longitude", "Longitude", "number"),
      f("capacity", "Concurrent order capacity", "number"),
      {
        name: "status",
        label: "Status",
        options: ["ACTIVE", "BUSY", "FULL", "MAINTENANCE", "INACTIVE"].map(
          (s) => ({ value: s, label: label(s) }),
        ),
        value: "ACTIVE",
      },
    ],
    inventory: [
      {
        name: "warehouseId",
        label: "Warehouse",
        options: selectOptions(warehouses.data),
      },
      {
        name: "productId",
        label: "Product",
        options: selectOptions(products.data),
      },
      f("quantity", "Units received", "number"),
    ],
    vehicles: [
      f("registrationNumber", "Registration number"),
      {
        name: "type",
        label: "Vehicle type",
        options: ["BIKE", "VAN", "TRUCK"].map((s) => ({
          value: s,
          label: label(s),
        })),
      },
      f("capacityKg", "Capacity (kg)", "number"),
    ],
    drivers: [
      f("name", "Driver name"),
      { name: "userId", label: "Driver user ID (optional)", required: false },
      {
        name: "vehicleId",
        label: "Vehicle",
        options: selectOptions(vehicles.data, "registration_number"),
      },
      f("latitude", "Latitude", "number"),
      f("longitude", "Longitude", "number"),
    ],
    addresses: [
      f("label", "Address label"),
      f("address", "Address"),
      f("latitude", "Latitude", "number"),
      f("longitude", "Longitude", "number"),
    ],
  };
  const canAdd = !!fields[resource];
  async function mutate(fn: () => Promise<any>) {
    setError("");
    try {
      await fn();
      await client.invalidateQueries();
    } catch (e) {
      setError((e as Error).message);
    }
  }
  return (
    <>
      <Heading
        title={titleCase(resource)}
        description={
          {
            warehouses:
              "Your fulfillment network. Capacity, availability, and location.",
            inventory:
              "Know what is on hand, what is reserved, and what is ready to move.",
            drivers: "The people who keep your network moving.",
            shipments: "Follow every shipment from dispatch to delivery.",
            alerts: "Exceptions that deserve a closer look.",
            audit: "An accountable record of important operations.",
          }[resource] ||
          "Manage the records that keep your delivery network connected."
        }
      >
        {canAdd && (
          <button className="primary" onClick={() => setAdding(true)}>
            <Plus size={17} />
            {resource === "inventory"
              ? "Receive stock"
              : `Add ${resource === "addresses" ? "address" : resource.slice(0, -1)}`}
          </button>
        )}
      </Heading>
      {error && <div className="error">{error}</div>}
      <div className="panel">
        <div className="panel-heading">
          <h2>{q.data?.length || 0} records on this page</h2>
          <button
            className="icon-button"
            aria-label="Refresh records"
            onClick={() => q.refetch()}
          >
            <RefreshCw size={17} />
          </button>
        </div>
        {q.isPending || q.error ? (
          <Loading error={q.error} />
        ) : !q.data?.length ? (
          <Empty />
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  {columns[resource].map((c) => (
                    <th key={c}>{titleCase(c)}</th>
                  ))}
                  <th />
                </tr>
              </thead>
              <tbody>
                {q.data.map((r, i) => (
                  <tr key={r.id || `${r.warehouse_id}-${r.product_id}-${i}`}>
                    {columns[resource].map((c) => (
                      <td key={c}>
                        {c === "status" || c === "role" ? (
                          <Badge value={r[c]} />
                        ) : c === "id" || c === "resource_id" ? (
                          <span className="mono">
                            {String(r[c]).slice(0, 12)}
                          </span>
                        ) : c.endsWith("_at") || c === "eta" ? (
                          date(r[c])
                        ) : typeof r[c] === "boolean" ? (
                          r[c] ? (
                            "Yes"
                          ) : (
                            "No"
                          )
                        ) : c === "price" ? (
                          money(r[c])
                        ) : (
                          String(r[c] ?? "—")
                        )}
                      </td>
                    ))}
                    <td>
                      {resource === "shipments" && (
                        <Link className="text-link" to={`/tracking/${r.id}`}>
                          Track <ArrowUpRight size={15} />
                        </Link>
                      )}
                      {resource === "users" && (
                        <button
                          className="text-link"
                          onClick={() => setEdit(r)}
                        >
                          Manage
                        </button>
                      )}
                      {resource === "alerts" && !r.resolved && (
                        <button
                          className="text-link"
                          onClick={() =>
                            mutate(() => post(`/api/alerts/${r.id}/resolve`))
                          }
                        >
                          Resolve
                        </button>
                      )}
                      {resource === "addresses" && (
                        <button
                          className="text-link danger"
                          onClick={() =>
                            mutate(() =>
                              api(`/api/addresses/${r.id}`, {
                                method: "DELETE",
                              }),
                            )
                          }
                        >
                          Remove
                        </button>
                      )}
                      {resource === "drivers" && (
                        <button
                          className="text-link"
                          onClick={() => setEdit(r)}
                        >
                          Update location
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <Pagination page={page} setPage={setPage} count={q.data?.length || 0} />
      </div>
      {adding && (
        <Dialog
          title={
            resource === "inventory"
              ? "Receive inventory"
              : `Add ${resource === "addresses" ? "address" : resource.slice(0, -1)}`
          }
          fields={fields[resource]}
          close={() => setAdding(false)}
          submit={async (d) => {
            if (resource === "drivers" && !d.userId) d.userId = null;
            await post(
              resource === "inventory"
                ? "/api/inventory/stock"
                : `/api/${resource}`,
              d,
            );
            await client.invalidateQueries();
          }}
        />
      )}
      {edit && (
        <Dialog
          title={
            resource === "users" ? "Manage account" : "Update driver location"
          }
          fields={
            resource === "users"
              ? [
                  {
                    name: "role",
                    label: "Role",
                    value: edit.role,
                    options: ["ADMIN", "OPERATOR", "DRIVER", "CUSTOMER"].map(
                      (s) => ({ value: s, label: label(s) }),
                    ),
                  },
                  {
                    name: "active",
                    label: "Account active",
                    value: String(edit.active),
                    options: [
                      { value: "true", label: "Yes" },
                      { value: "false", label: "No" },
                    ],
                  },
                  {
                    name: "forceReset",
                    label: "Require password change",
                    value: String(edit.force_reset),
                    options: [
                      { value: "true", label: "Yes" },
                      { value: "false", label: "No" },
                    ],
                  },
                ]
              : [
                  f("latitude", "Latitude", "number", edit.latitude),
                  f("longitude", "Longitude", "number", edit.longitude),
                ]
          }
          close={() => setEdit(null)}
          submit={async (d) => {
            if (resource === "users")
              await api(`/api/users/${edit.id}`, {
                method: "PATCH",
                body: JSON.stringify({
                  role: d.role,
                  active: d.active === "true",
                  forceReset: d.forceReset === "true",
                }),
              });
            else await post(`/api/tracking/drivers/${edit.id}/position`, d);
            await client.invalidateQueries();
          }}
        />
      )}
    </>
  );
}

function RouteView({
  nodes,
  position,
}: {
  nodes: number[][];
  position?: number[];
}) {
  const points = position ? [...nodes, position] : nodes;
  if (!points.length)
    return <Empty text="Route coordinates are unavailable." />;
  const minLat = Math.min(...points.map((p) => p[0])),
    maxLat = Math.max(...points.map((p) => p[0])),
    minLon = Math.min(...points.map((p) => p[1])),
    maxLon = Math.max(...points.map((p) => p[1]));
  const xy = (p: number[]) => [
    70 + ((p[1] - minLon) / (maxLon - minLon || 1)) * 560,
    280 - ((p[0] - minLat) / (maxLat - minLat || 1)) * 200,
  ];
  return (
    <div className="route-map">
      <div className="map-label">
        <MapPin size={14} /> GEODESIC ROUTE · NOT ROAD NAVIGATION
      </div>
      <svg
        viewBox="0 0 700 360"
        role="img"
        aria-label="Estimated route between warehouse and destination"
      >
        <defs>
          <pattern
            id="grid"
            width="35"
            height="35"
            patternUnits="userSpaceOnUse"
          >
            <path
              d="M 35 0 L 0 0 0 35"
              fill="none"
              stroke="#dce6dd"
              strokeWidth="1"
            />
          </pattern>
        </defs>
        <rect width="700" height="360" fill="url(#grid)" />
        <polyline
          points={nodes.map((p) => xy(p).join(",")).join(" ")}
          fill="none"
          stroke="#2f795d"
          strokeWidth="4"
          strokeDasharray="8 6"
        />
        {nodes.map((p, i) => {
          const [x, y] = xy(p);
          return (
            <g key={i}>
              <circle
                cx={x}
                cy={y}
                r="13"
                fill={i === 0 ? "#183f31" : "#cfdf9b"}
                stroke="white"
                strokeWidth="4"
              />
              <text
                x={x}
                y={y - 24}
                textAnchor="middle"
                fill="#254936"
                fontSize="13"
                fontWeight="600"
              >
                {i === 0
                  ? "Warehouse"
                  : i === nodes.length - 1
                    ? "Destination"
                    : `Stop ${i}`}
              </text>
            </g>
          );
        })}
        {position && (
          <g>
            <circle
              cx={xy(position)[0]}
              cy={xy(position)[1]}
              r="22"
              fill="#429b7a"
              opacity=".15"
            />
            <circle
              cx={xy(position)[0]}
              cy={xy(position)[1]}
              r="7"
              fill="#168562"
              stroke="white"
              strokeWidth="3"
            />
          </g>
        )}
      </svg>
      <div className="map-caption">
        <span>
          <i /> Last reported driver position
        </span>
        <span>Distances are estimates</span>
      </div>
    </div>
  );
}
export function Tracking() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [selected, setSelected] = useState(""),
    [live, setLive] = useState<Row | null>(null),
    [connection, setConnection] = useState("Connecting");
  const shipments = useQuery({
    queryKey: ["tracking-shipments"],
    queryFn: () => api<Row[]>("/api/shipments?size=100"),
  });
  const q = useQuery({
    queryKey: ["tracking", id],
    queryFn: () => api<Row>(`/api/tracking/${id}`),
    enabled: !!id,
  });
  useEffect(() => {
    if (!id) return;
    setLive(null);
    const abort = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function connect() {
      try {
        setConnection("Connecting");
        await api(`/api/tracking/${id}`);
        const r = await fetch(`/api/tracking/${id}/stream`, {
          headers: { Authorization: `Bearer ${getSession()?.accessToken}` },
          signal: abort.signal,
        });
        if (!r.ok || !r.body) throw new Error("Tracking stream unavailable");
        setConnection("Live");
        const reader = r.body.getReader(),
          decoder = new TextDecoder();
        let buffer = "";
        while (!abort.signal.aborted) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder
            .decode(value, { stream: true })
            .replaceAll("\r\n", "\n");
          let end;
          while ((end = buffer.indexOf("\n\n")) >= 0) {
            const event = buffer.slice(0, end);
            buffer = buffer.slice(end + 2);
            const data = event
              .split("\n")
              .filter((line) => line.startsWith("data:"))
              .map((line) => line.slice(5).trim())
              .join("\n");
            if (data) setLive(JSON.parse(data));
          }
        }
        if (!abort.signal.aborted) {
          setConnection("Reconnecting");
          timer = setTimeout(connect, 2000);
        }
      } catch {
        if (!abort.signal.aborted) {
          setConnection("Disconnected · retrying");
          timer = setTimeout(connect, 5000);
        }
      }
    }
    connect();
    return () => {
      abort.abort();
      clearTimeout(timer);
    };
  }, [id]);
  const data = live || q.data;
  return (
    <>
      <Heading
        eyebrow="LAST-MILE VISIBILITY"
        title="Live tracking"
        description="Actual shipment events and reported driver positions, in one view."
      >
        <span
          className={`live-pill ${connection === "Live" ? "connected" : ""}`}
        >
          <Radio size={15} />
          {id ? connection : "Select a shipment"}
        </span>
      </Heading>
      <div className="panel tracking-selector">
        <Package size={20} />
        <select
          aria-label="Select shipment"
          value={id || selected}
          onChange={(e) => {
            setSelected(e.target.value);
            navigate(`/tracking/${e.target.value}`);
          }}
        >
          <option value="">Choose a shipment to track</option>
          {shipments.data?.map((s) => (
            <option key={s.id} value={s.id}>
              FF-{s.id.slice(0, 8).toUpperCase()} · {s.address} ·{" "}
              {label(s.status)}
            </option>
          ))}
        </select>
      </div>
      {id ? (
        !data ? (
          <Loading error={q.error} />
        ) : (
          <>
            <div className="tracking-top">
              <div>
                <Badge value={data.status} />
                <h2>{data.address}</h2>
                <p className="muted">Shipment {data.id}</p>
              </div>
              <div className="eta-card">
                <Clock size={20} />
                <div>
                  <span>
                    {data.status === "DELIVERED"
                      ? "Delivered at"
                      : "Estimated arrival"}
                  </span>
                  <b>{date(data.delivered_at || data.eta)}</b>
                </div>
              </div>
            </div>
            <div className="detail-grid">
              <article className="panel">
                <RouteView
                  nodes={JSON.parse(data.nodes_json)}
                  position={
                    data.latitude == null || data.longitude == null
                      ? undefined
                      : [data.latitude, data.longitude]
                  }
                />
                <div className="route-stats">
                  <div>
                    <span>Estimated distance</span>
                    <b>{Number(data.distance_km).toFixed(1)} km</b>
                  </div>
                  <div>
                    <span>Initial travel estimate</span>
                    <b>{data.duration_minutes} min</b>
                  </div>
                  <div>
                    <span>Assigned driver</span>
                    <b>{data.driver_name}</b>
                  </div>
                </div>
                <div className="padded">
                  <small className="muted">
                    Last driver report: {date(data.position_updated_at)}. ETA
                    uses estimated speed and stop time; no live traffic data.
                  </small>
                </div>
              </article>
              <article className="panel padded">
                <span className="eyebrow">SHIPMENT JOURNEY</span>
                <h2>Every handoff, recorded</h2>
                <div className="timeline">
                  {data.events.map((e: Row) => (
                    <div key={e.id}>
                      <span className="timeline-dot">
                        <Check size={12} />
                      </span>
                      <b>{label(e.status)}</b>
                      <small>{date(e.created_at)}</small>
                      <p>{e.detail}</p>
                    </div>
                  ))}
                </div>
              </article>
            </div>
          </>
        )
      ) : (
        <Empty text="Choose an assigned shipment to see its route and event timeline." />
      )}
    </>
  );
}
export function RoutePlanner() {
  const q = useQuery({
    queryKey: ["routes"],
    queryFn: () => api<Row[]>("/api/routes?size=100"),
  });
  const [selected, setSelected] = useState("");
  const route = q.data?.find((r) => r.id === selected) || q.data?.[0];
  return (
    <>
      <Heading
        title="Routes & estimates"
        description="Explainable route estimates for every assigned shipment."
      />
      {q.isPending || q.error ? (
        <Loading error={q.error} />
      ) : !route ? (
        <Empty text="Assign a driver to a packed order to generate a route." />
      ) : (
        <>
          <div className="panel tracking-selector">
            <MapPin />
            <select
              aria-label="Select route"
              value={route.id}
              onChange={(e) => setSelected(e.target.value)}
            >
              {q.data?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.address} · {Number(r.distance_km).toFixed(1)} km
                </option>
              ))}
            </select>
          </div>
          <div className="panel">
            <RouteView nodes={JSON.parse(route.nodes_json)} />
            <div className="route-stats">
              <div>
                <span>Estimated distance</span>
                <b>{Number(route.distance_km).toFixed(1)} km</b>
              </div>
              <div>
                <span>Travel + stop estimate</span>
                <b>{route.duration_minutes} min</b>
              </div>
              <div>
                <span>Distance source</span>
                <b>Geodesic estimate</b>
              </div>
              <Link className="text-link" to={`/tracking/${route.shipment_id}`}>
                Track shipment <ArrowUpRight size={16} />
              </Link>
            </div>
          </div>
          <div className="notice">
            Routes currently connect the warehouse and destination directly.
            They are planning estimates, not turn-by-turn road navigation.
          </div>
        </>
      )}
    </>
  );
}
export function Account() {
  const session = getSession()!;
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const client = useQueryClient();
  async function change(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await post(
        "/api/auth/password",
        Object.fromEntries(new FormData(e.currentTarget)),
      );
      setSession(null);
      client.clear();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Heading
        title="Profile & security"
        description="Your account details and access settings."
      />
      <div className="detail-grid">
        <article className="panel padded">
          <span className="eyebrow">YOUR ACCOUNT</span>
          <h2>{session.user.name}</h2>
          <div className="key-values">
            <div>
              <span>Email</span>
              <b>{session.user.email}</b>
            </div>
            <div>
              <span>Access level</span>
              <Badge value={session.user.role} />
            </div>
          </div>
          <p className="muted">
            An administrator manages your role and account status. Your password
            is never visible to them.
          </p>
        </article>
        <article className="panel padded">
          <span className="eyebrow">KEEP YOUR ACCOUNT SECURE</span>
          <h2>Change password</h2>
          <form onSubmit={change}>
            <label>
              Current password
              <input
                name="currentPassword"
                type="password"
                autoComplete="current-password"
                required
              />
            </label>
            <label>
              New password
              <input
                name="newPassword"
                type="password"
                minLength={12}
                maxLength={72}
                autoComplete="new-password"
                required
              />
            </label>
            <p className="muted">
              Use 12–72 characters. Changing your password signs out all
              sessions.
            </p>
            {error && <div className="error">{error}</div>}
            <button className="primary" disabled={busy}>
              {busy ? "Updating…" : "Update password"}
              <ArrowUpRight size={16} />
            </button>
          </form>
        </article>
      </div>
    </>
  );
}
export function Health() {
  const q = useQuery({
    queryKey: ["health"],
    queryFn: () => api("/actuator/health"),
  });
  return (
    <>
      <Heading
        title="System health"
        description="Current backend and database readiness."
      />
      <div className="panel padded">
        {q.isPending || q.error ? (
          <Loading error={q.error} />
        ) : (
          <>
            <Badge value={q.data.status} />
            <div className="key-values">
              {Object.entries(q.data.components || {}).map(
                ([name, component]) => (
                  <div key={name}>
                    <span>{titleCase(name)}</span>
                    <Badge value={(component as Row).status} />
                  </div>
                ),
              )}
            </div>
            <p className="muted">
              Redis is optional and excluded from core readiness. Its cache
              failures fall back to PostgreSQL.
            </p>
            <button className="secondary" onClick={() => q.refetch()}>
              <RefreshCw size={15} />
              Check again
            </button>
          </>
        )}
      </div>
    </>
  );
}

export function DriverWorkspace() {
  const q = useQuery({
    queryKey: ["own-driver"],
    queryFn: () => api<Row[]>("/api/drivers"),
  });
  const client = useQueryClient();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const driver = q.data?.[0];
  async function update(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!driver) return;
    setBusy(true);
    setError("");
    const data = new FormData(e.currentTarget);
    try {
      await post(`/api/tracking/drivers/${driver.id}/position`, {
        latitude: Number(data.get("latitude")),
        longitude: Number(data.get("longitude")),
      });
      await client.invalidateQueries();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function availability(status: string) {
    if (!driver) return;
    setBusy(true);
    setError("");
    try {
      await api(`/api/drivers/${driver.id}/availability`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      });
      await client.invalidateQueries();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Heading
        title="Your driver profile"
        description="Manage your availability and report your current location."
      />
      {error && <div className="error">{error}</div>}
      {q.isPending || q.error ? (
        <Loading error={q.error} />
      ) : !driver ? (
        <Empty text="An operator must link a driver profile to your account." />
      ) : (
        <div className="detail-grid">
          <article className="panel padded">
            <Badge value={driver.status} />
            <h2>{driver.name}</h2>
            <div className="key-values">
              <div>
                <span>Assigned vehicle</span>
                <b>{driver.registration_number}</b>
              </div>
              <div>
                <span>Capacity</span>
                <b>{driver.capacity_kg} kg</b>
              </div>
              <div>
                <span>Current workload</span>
                <b>{driver.workload} active shipment</b>
              </div>
            </div>
            <label>
              Availability
              <select
                disabled={busy || driver.workload > 0}
                value={driver.status}
                onChange={(e) => availability(e.target.value)}
              >
                {[
                  ...new Set([
                    driver.status,
                    "AVAILABLE",
                    "OFFLINE",
                    "ON_LEAVE",
                  ]),
                ].map((s) => (
                  <option key={s} value={s}>
                    {label(s)}
                  </option>
                ))}
              </select>
            </label>
          </article>
          <article className="panel padded">
            <span className="eyebrow">LOCATION REPORT</span>
            <h2>Update your position</h2>
            <form onSubmit={update}>
              <label>
                Latitude
                <input
                  required
                  type="number"
                  step="any"
                  min={-90}
                  max={90}
                  name="latitude"
                  defaultValue={driver.latitude}
                />
              </label>
              <label>
                Longitude
                <input
                  required
                  type="number"
                  step="any"
                  min={-180}
                  max={180}
                  name="longitude"
                  defaultValue={driver.longitude}
                />
              </label>
              <p className="muted">
                Last report: {date(driver.updated_at)}. Your current
                assignment’s ETA will be recalculated.
              </p>
              <button className="primary" disabled={busy}>
                <MapPin size={16} />
                Report location
              </button>
            </form>
          </article>
        </div>
      )}
    </>
  );
}
