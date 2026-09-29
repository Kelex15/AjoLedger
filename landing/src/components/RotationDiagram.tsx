const CENTER = 200
const SEAT_RADIUS = 132
const TOTAL_SEATS = 6
const PAID_SEAT = 2

const seats = Array.from({ length: TOTAL_SEATS }, (_, index) => {
  const angle = (index * (360 / TOTAL_SEATS) - 90) * (Math.PI / 180)
  return {
    position: index + 1,
    x: CENTER + SEAT_RADIUS * Math.cos(angle),
    y: CENTER + SEAT_RADIUS * Math.sin(angle),
  }
})

/** Six members, one pot, and whose round it is. The whole hero visual is this one figure. */
export function RotationDiagram() {
  const paid = seats[PAID_SEAT]

  return (
    <svg viewBox="0 0 400 400" role="img" aria-labelledby="rotation-title" className="w-full">
      <title id="rotation-title">
        A circle of six members. Everyone contributes each round, and the full pot goes to one member at a
        time.
      </title>

      <defs>
        <marker id="payout-arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto">
          <path d="M 0 0 L 10 5 L 0 10 z" className="fill-primary" />
        </marker>
      </defs>

      <circle cx={CENTER} cy={CENTER} r={SEAT_RADIUS} fill="none" className="stroke-border" strokeWidth="1.5" />

      {seats.map((seat) =>
        seat.position === paid.position ? null : (
          <line
            key={seat.position}
            x1={seat.x}
            y1={seat.y}
            x2={CENTER}
            y2={CENTER}
            className="stroke-muted-foreground opacity-40"
            strokeWidth="1.5"
            strokeDasharray="3 5"
          />
        ),
      )}

      <line
        x1={CENTER}
        y1={CENTER}
        x2={paid.x}
        y2={paid.y}
        className="stroke-primary"
        strokeWidth="2.5"
        markerEnd="url(#payout-arrow)"
      />

      <circle cx={CENTER} cy={CENTER} r="52" className="fill-card stroke-border" strokeWidth="1.5" />
      <text
        x={CENTER}
        y={CENTER - 8}
        textAnchor="middle"
        className="fill-muted-foreground text-[11px] tracking-widest uppercase"
      >
        Pot
      </text>
      <text
        x={CENTER}
        y={CENTER + 18}
        textAnchor="middle"
        data-numeric
        className="fill-foreground font-heading text-xl font-semibold"
      >
        $600
      </text>

      {seats.map((seat) => {
        const isPaid = seat.position === paid.position
        return (
          <g key={seat.position}>
            <circle
              cx={seat.x}
              cy={seat.y}
              r={isPaid ? 26 : 20}
              className={isPaid ? 'fill-primary' : 'fill-muted stroke-border'}
              strokeWidth="1.5"
            />
            {isPaid && (
              <circle
                cx={seat.x}
                cy={seat.y}
                r="34"
                fill="none"
                className="stroke-primary opacity-30"
                strokeWidth="1.5"
              />
            )}
            <text
              x={seat.x}
              y={seat.y + 5}
              textAnchor="middle"
              data-numeric
              className={`text-sm font-semibold ${isPaid ? 'fill-primary-foreground' : 'fill-muted-foreground'}`}
            >
              {seat.position}
            </text>
          </g>
        )
      })}
    </svg>
  )
}
