const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

const storiesFn = `
  const renderStories = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">STORY MANAGEMENT</p>
            <h1>Stories</h1>
            <p className="muted">Review DENO stories, visibility, reports and expiry status.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <div className="summary-grid">
          <div className="summary-card"><span>Total Stories</span><strong>—</strong></div>
          <div className="summary-card"><span>Active</span><strong>—</strong></div>
          <div className="summary-card"><span>Reported</span><strong>—</strong></div>
          <div className="summary-card"><span>Expired</span><strong>—</strong></div>
        </div>

        <div className="toolbar">
          <input placeholder="Search story, username or ID..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Active</option>
            <option>Reported</option>
            <option>Removed</option>
            <option>Expired</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Privacy</option>
            <option>Public</option>
            <option>Followers</option>
            <option>Private</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Media</option>
            <option>Photo</option>
            <option>Video</option>
          </select>
        </div>

        <div className="posts-table-wrap">
          <table className="posts-table">
            <thead>
              <tr>
                <th>STORY</th>
                <th>AUTHOR</th>
                <th>MEDIA</th>
                <th>VISIBILITY</th>
                <th>STATUS</th>
                <th>REPORTS</th>
                <th>ACTION</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="post-placeholder">
                    <strong>No live stories</strong>
                    <span>Backend connection required</span>
                  </div>
                </td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td><span className="status-badge">—</span></td>
                <td>—</td>
                <td><button className="view-button" disabled>View</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="info-box">
          <strong>Story lifecycle</strong>
          <p>Published → Active → Expired. Reported stories can enter moderation before expiry.</p>
        </div>

        <div className="demo-notice">
          No fake story data is being shown. Real stories will be loaded from the DENO backend.
        </div>
      </div>
    </section>
  )
`

if (!s.includes('const renderStories = () =>')) {
  const marker = '  const renderReports = () => ('
  if (!s.includes(marker)) throw new Error('renderReports marker not found')
  s = s.replace(marker, storiesFn + '\n' + marker)
}

if (!s.includes("if (active === 'Stories') return renderStories()")) {
  const marker = "    if (active === 'Reels') return renderReels()"
  if (!s.includes(marker)) throw new Error('Reels navigation marker not found')
  s = s.replace(
    marker,
    marker + "\n    if (active === 'Stories') return renderStories()"
  )
}

fs.writeFileSync(file, s)
console.log('Stories page connected successfully.')
