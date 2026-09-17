import { useState, type FormEvent } from "react";
import { post } from "./api";

export default function MultiStopPlan() {
  const [result, setResult] = useState<{
    stopOrder: number[];
    distanceKm: number;
    durationMinutes: number;
  } | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function calculate(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    setBusy(true);
    setError("");
    setResult(null);
    try {
      const lines = String(data.get("stops")).trim().split(/\r?\n/);
      if (lines.length > 50) throw new Error("Use at most 50 stops.");
      const stops = lines.map((line) => {
        const parts = line.split(",").map((v) => v.trim());
        if (
          parts.length !== 2 ||
          parts.some((v) => v === "" || !Number.isFinite(Number(v)))
        )
          throw new Error(
            "Enter each stop as latitude, longitude on a separate line.",
          );
        return { latitude: Number(parts[0]), longitude: Number(parts[1]) };
      });
      setResult(
        await post("/api/routes/plan", {
          start: {
            latitude: Number(data.get("latitude")),
            longitude: Number(data.get("longitude")),
          },
          stops,
          speedKmh: Number(data.get("speed")),
        }),
      );
    } catch (e) {
      setError(e instanceof Error ? e.message : "Route calculation failed");
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel route-planning-panel">
      <h2>Plan a multi-stop route</h2>
      <p>
        Nearest-neighbor estimate, starting at your origin and ending at the
        last stop. Plans are calculated on demand and are not saved or assigned.
      </p>
      <form onSubmit={calculate}>
        <div className="form-grid">
          <label>
            Start latitude
            <input
              name="latitude"
              type="number"
              min="-90"
              max="90"
              step="any"
              required
            />
          </label>
          <label>
            Start longitude
            <input
              name="longitude"
              type="number"
              min="-180"
              max="180"
              step="any"
              required
            />
          </label>
          <label>
            Planning speed (km/h)
            <input
              name="speed"
              type="number"
              min="1"
              max="120"
              defaultValue="35"
              required
            />
          </label>
        </div>
        <label>
          Stops (latitude, longitude per line)
          <textarea
            name="stops"
            rows={4}
            maxLength={10000}
            required
            placeholder="20.30, 85.82"
          />
        </label>
        <button className="btn primary" disabled={busy}>
          {busy ? "Calculating…" : "Calculate route"}
        </button>
      </form>
      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      {result && (
        <div className="notice" role="status">
          <strong>
            Visit stops: {result.stopOrder.map((i) => i + 1).join(" → ")}
          </strong>
          <p>
            {result.distanceKm.toFixed(2)} km · {result.durationMinutes} minutes
            (includes 5 minutes per stop). Geodesic estimate; no road or traffic
            data.
          </p>
        </div>
      )}
    </section>
  );
}
