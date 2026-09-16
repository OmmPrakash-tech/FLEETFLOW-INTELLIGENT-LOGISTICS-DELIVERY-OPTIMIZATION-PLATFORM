import { useEffect, useState, type FormEvent } from "react";
import {
  NavLink,
  Route,
  Routes,
  useNavigate,
  Navigate,
} from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  CheckCheck,
  ArrowUpRight,
  Boxes,
  ChartNoAxesCombined,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  Compass,
  House,
  LogOut,
  MapPin,
  Package,
  Radio,
  Route as RouteIcon,
  Settings,
  ShieldCheck,
  Truck,
  Users,
  Warehouse,
  Bell,
  Activity,
  Menu,
  X,
} from "lucide-react";
import { api, getSession, setSession, post, type Session } from "./api";
import {
  Dashboard,
  DataPage,
  Orders,
  OrderDetail,
  Tracking,
  Account,
  Health,
  RoutePlanner,
  DriverWorkspace,
} from "./Pages";

export function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark">
        <Boxes size={23} />
      </span>
      <span>
        fleetflow<span className="brand-dot">.</span>
      </span>
    </div>
  );
}
function Auth() {
  const navigate = useNavigate();
  const [mode, setMode] = useState(
    location.pathname === "/reset-password" ? "reset" : "login",
  );
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setNotice("");
    const d = Object.fromEntries(new FormData(e.currentTarget));
    try {
      if (mode === "forgot") {
        await post("/api/auth/forgot-password", { email: d.email });
        setNotice("If this account exists, a recovery email has been sent.");
      } else if (mode === "reset") {
        await post("/api/auth/reset-password", {
          token: new URLSearchParams(location.hash.slice(1)).get("token"),
          password: d.password,
        });
        history.replaceState(null, "", "/");
        setMode("login");
        setNotice("Password reset. Sign in with your new password.");
      } else {
        const s = await post(`/api/auth/${mode}`, d);
        setSession(s);
        navigate("/");
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="auth-shell">
      <div className="auth-story">
        <Brand />
        <div>
          <span className="eyebrow light">
            THE DISTANCE BETWEEN PLANNED AND DELIVERED
          </span>
          <h1>
            Every parcel.
            <br />
            Every mile.
            <br />
            <em>Under control.</em>
          </h1>
          <p>
            A clear view of your logistics network, from warehouse shelves to
            the last doorstep.
          </p>
        </div>
        <div className="auth-route">
          <span>
            <Warehouse />
            Warehouse
          </span>
          <i />
          <span>
            <Truck />
            In transit
          </span>
          <i />
          <span>
            <MapPin />
            Delivered
          </span>
        </div>
        <small>FLEETFLOW / INTELLIGENT LOGISTICS</small>
      </div>
      <main className="auth-form">
        <span className="eyebrow">YOUR OPERATIONS, CONNECTED</span>
        <h2>
          {mode === "register"
            ? "Start your journey"
            : mode === "forgot"
              ? "Recover your account"
              : mode === "reset"
                ? "Set a new password"
                : "Welcome back"}
        </h2>
        <p className="muted">
          {mode === "login"
            ? "Sign in to your FleetFlow workspace."
            : "Secure access to your deliveries."}
        </p>
        <form onSubmit={submit}>
          {mode === "register" && (
            <label>
              Full name
              <input required name="name" maxLength={120} autoComplete="name" />
            </label>
          )}
          {mode !== "reset" && (
            <label>
              Email address
              <input
                required
                type="email"
                name="email"
                autoComplete="email"
                placeholder="you@company.com"
              />
            </label>
          )}
          {mode !== "forgot" && (
            <label>
              Password
              <input
                required
                type="password"
                name="password"
                minLength={mode === "login" ? 1 : 12}
                maxLength={72}
                autoComplete={
                  mode === "login" ? "current-password" : "new-password"
                }
              />
            </label>
          )}
          {error && (
            <div className="error" role="alert">
              {error}
            </div>
          )}
          {notice && <div className="notice">{notice}</div>}
          <button className="primary wide" disabled={busy}>
            {busy
              ? "Please wait…"
              : mode === "register"
                ? "Create account"
                : mode === "forgot"
                  ? "Send recovery email"
                  : mode === "reset"
                    ? "Reset password"
                    : "Sign in"}
            <ArrowUpRight size={18} />
          </button>
        </form>
        <div className="auth-links">
          <button
            onClick={() => {
              setMode(mode === "register" ? "login" : "register");
              setError("");
            }}
          >
            {mode === "register"
              ? "Already have an account? Sign in"
              : "New here? Create an account"}
          </button>
          <button
            onClick={() => {
              setMode(mode === "forgot" ? "login" : "forgot");
              setError("");
            }}
          >
            {mode === "forgot" ? "Back to sign in" : "Forgot password?"}
          </button>
        </div>
        <div className="auth-foot">
          <ShieldCheck size={16} /> Secure sessions · Role-based access
        </div>
      </main>
    </div>
  );
}
const operations = [
  ["/", "Overview", House],
  ["/orders", "Orders", Package],
  ["/shipments", "Shipments", Truck],
  ["/tracking", "Live tracking", Radio],
  ["/routes", "Routes", RouteIcon],
] as const;
const network = [
  ["/warehouses", "Warehouses", Warehouse],
  ["/inventory", "Inventory", Boxes],
  ["/products", "Products", Package],
  ["/drivers", "Drivers", Users],
  ["/vehicles", "Vehicles", Truck],
] as const;
export default function App() {
  const [session, update] = useState<Session | null>(getSession());
  const [mobile, setMobile] = useState(false);
  const client = useQueryClient();
  const navigate = useNavigate();
  useEffect(() => {
    const listener = () => update(getSession());
    window.addEventListener("session", listener);
    return () => window.removeEventListener("session", listener);
  }, []);
  if (!session) return <Auth />;
  const staff = ["ADMIN", "OPERATOR"].includes(session.user.role),
    admin = session.user.role === "ADMIN",
    driver = session.user.role === "DRIVER";
  const link = (item: readonly [string, string, any]) => {
    const [path, title, Icon] = item;
    return (
      <NavLink
        key={path}
        to={path}
        end={path === "/"}
        onClick={() => setMobile(false)}
      >
        <Icon size={18} />
        <span>{title}</span>
        <ChevronRight size={14} />
      </NavLink>
    );
  };
  async function logout() {
    try {
      await post("/api/auth/logout");
    } finally {
      setSession(null);
      client.clear();
      navigate("/");
    }
  }
  return (
    <div className="app-shell">
      <aside className={`sidebar ${mobile ? "open" : ""}`}>
        <Brand />
        <div className="workspace">
          <span className="workspace-avatar">F</span>
          <div>
            <b>FleetFlow workspace</b>
            <small>
              {staff
                ? "Operations console"
                : driver
                  ? "Driver workspace"
                  : "Customer portal"}
            </small>
          </div>
          <ChevronDown size={14} />
        </div>
        <div className="nav-heading">
          {staff ? "OPERATIONS" : "YOUR DELIVERIES"}
        </div>
        <nav>{operations.filter((i) => !driver || i[0] !== "/").map(link)}</nav>
        {staff && (
          <>
            <div className="nav-heading">NETWORK</div>
            <nav>{network.map(link)}</nav>
            <div className="nav-heading">MANAGEMENT</div>
            <nav>
              {link(["/analytics", "Analytics", ChartNoAxesCombined])}
              {link(["/alerts", "Alerts", Bell])}
              {admin && link(["/users", "Users", Users])}
              {admin && link(["/audit", "Audit logs", ClipboardList])}
              {admin && link(["/health", "System health", Activity])}
            </nav>
          </>
        )}
        {driver && <nav>{link(["/driver", "My driver profile", Truck])}</nav>}
        {!staff && (
          <nav>
            {!driver && link(["/history", "Delivery history", CheckCheck])}
            {link(["/addresses", "Addresses", MapPin])}
          </nav>
        )}
        <div className="sidebar-bottom">
          <div className="network-note">
            <span className="pulse" />
            <span>Connected operations</span>
          </div>
          {link(["/account", "Profile & security", Settings])}
          <button className="profile" onClick={logout}>
            <span className="avatar">
              {session.user.name.slice(0, 2).toUpperCase()}
            </span>
            <span>
              <b>{session.user.name}</b>
              <small>{session.user.role.toLowerCase()}</small>
            </span>
            <LogOut size={16} />
          </button>
        </div>
      </aside>
      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="mobile-toggle icon-button"
              onClick={() => setMobile(!mobile)}
              aria-label="Toggle navigation"
            >
              {mobile ? <X /> : <Menu />}
            </button>
            <span>Workspace</span>
            <ChevronRight size={14} />
            <strong>
              {staff ? "Operations" : driver ? "Driver" : "Deliveries"}
            </strong>
          </div>
          <div className="top-right">
            <span className="today">
              {new Date().toLocaleDateString("en-IN", {
                day: "numeric",
                month: "short",
                year: "numeric",
              })}
            </span>
            <span className="separator" />
            <NavLink
              to={staff ? "/alerts" : "/tracking"}
              className="icon-button"
              aria-label={staff ? "Alerts" : "Tracking"}
            >
              <Bell size={19} />
            </NavLink>
            <span className="avatar small">{session.user.name[0]}</span>
          </div>
        </header>
        <main className="content">
          {session.user.force_reset && (
            <div className="error">
              Your administrator requires a password change.{" "}
              <NavLink to="/account">Update your password</NavLink>.
            </div>
          )}
          <Routes>
            <Route
              path="/"
              element={
                driver ? (
                  <Navigate to="/orders" replace />
                ) : (
                  <Dashboard staff={staff} />
                )
              }
            />
            <Route
              path="/analytics"
              element={staff ? <Dashboard staff /> : <Navigate to="/" />}
            />
            <Route path="/orders" element={<Orders staff={staff} />} />
            <Route path="/orders/:id" element={<OrderDetail staff={staff} />} />
            <Route path="/tracking" element={<Tracking />} />
            <Route path="/tracking/:id" element={<Tracking />} />
            <Route path="/routes" element={<RoutePlanner />} />
            {[
              "shipments",
              "addresses",
              ...(staff
                ? [
                    "warehouses",
                    "inventory",
                    "products",
                    "drivers",
                    "vehicles",
                    "alerts",
                  ]
                : []),
              ...(admin ? ["users", "audit"] : []),
            ].map((name) => (
              <Route
                key={name}
                path={`/${name}`}
                element={<DataPage key={name} resource={name} />}
              />
            ))}
            <Route
              path="/driver"
              element={driver ? <DriverWorkspace /> : <Navigate to="/" />}
            />
            <Route path="/history" element={<Orders staff={staff} history />} />
            <Route path="/account" element={<Account />} />
            <Route
              path="/health"
              element={admin ? <Health /> : <Navigate to="/" />}
            />
            <Route path="*" element={<Navigate to="/" />} />
          </Routes>
          <footer className="page-footer">
            <span>
              FLEETFLOW <b>/</b> LOGISTICS, IN SYNC
            </span>
            <span>
              Built for the journey ahead <Compass size={13} />
            </span>
          </footer>
        </main>
      </div>
    </div>
  );
}
