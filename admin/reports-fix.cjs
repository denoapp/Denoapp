const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

const reportsFn = `
  const renderReports = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">SAFETY & MODERATION</p>
            <h1>Reports</h1>
            <p className="muted">Review user reports and take authorized moderation actions.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <div className="summary-grid">
          <div className="summary-card"><span>Total Reports</span><strong>—</strong></div>
          <div className="summary-card"><span>Pending</span><strong>—</strong></div>
          <div className="summary-card"><span>Reviewing</span><strong>—</strong></div>
          <div className="summary-card"><span>Resolved</span><strong>—</strong></div>
        </div>

        <div className="toolbar">
          <input placeholder="Search report, user or content ID..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Pending</option>
            <option>Reviewing</option>
            <option>Resolved</option>
            <option>Dismissed</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Types</option>
            <option>Post</option>
            <option>Reel</option>
            <option>Story</option>
            <option>User</option>
            <option>Message</option>
            <option>Group</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Priority</option>
            <option>High</option>
            <option>Medium</option>
            <option>Low</option>
          </select>
        </div>

        <div className="posts-table-wrap">
          <table className="posts-table">
            <thead>
              <tr>
                <th>REPORT</th>
                <th>REPORTER</th>
                <th>CONTENT</th>
                <th>TYPE</th>
                <th>PRIORITY</th>
                <th>STATUS</th>
                <th>ACTION</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="post-placeholder">
                    <strong>No live reports</strong>
                    <span>Backend connection required</span>
                  </div>
                </td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td><span className="status-badge">—</span></td>
                <td><span className="status-badge">—</span></td>
                <td><button className="view-button" disabled>Review</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="info-box">
          <strong>Report review workflow</strong>
          <p>Pending → Reviewing → Action / Dismiss → Resolved. All moderation actions should be recorded in the audit log.</p>
        </div>

        <div className="demo-notice">
          No fake report data is being shown. Real reports will be loaded from the DENO backend.
        </div>
      </div>
    </section>
  )
`

const marker = "  const renderReports = () => ("

if (s.includes(marker)) {
  const start = s.indexOf(marker)
  const next = s.indexOf("\n  const ", start + marker.length)

  if (next === -1) {
    throw new Error('Could not locate next render function')
  }

  s = s.slice(0, start) + reportsFn.trimEnd() + "\n\n" + s.slice(next + 1)
}

fs.writeFileSync(file, s)
console.log('Reports page updated successfully.')
