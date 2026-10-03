const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

const reelsFn = `
  const renderReels = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">VIDEO MANAGEMENT</p>
            <h1>Reels</h1>
            <p className="muted">Review DENO reels, visibility, reports and moderation status.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <div className="summary-grid">
          <div className="summary-card"><span>Total Reels</span><strong>—</strong></div>
          <div className="summary-card"><span>Published</span><strong>—</strong></div>
          <div className="summary-card"><span>Reported</span><strong>—</strong></div>
          <div className="summary-card"><span>Removed</span><strong>—</strong></div>
        </div>

        <div className="toolbar">
          <input placeholder="Search reel, username or ID..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Published</option>
            <option>Reported</option>
            <option>Removed</option>
            <option>Pending Review</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Privacy</option>
            <option>Public</option>
            <option>Followers</option>
            <option>Private</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Media</option>
            <option>Video</option>
            <option>Processing</option>
            <option>Failed</option>
          </select>
        </div>

        <div className="posts-table-wrap">
          <table className="posts-table">
            <thead>
              <tr>
                <th>REEL</th>
                <th>CREATOR</th>
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
                    <strong>No live reels</strong>
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
          <strong>Reel moderation workflow</strong>
          <p>Upload → Automated checks → Publish / Review → Restore / Remove.</p>
        </div>

        <div className="demo-notice">
          No fake reel data is being shown. Real reels will be loaded from the DENO backend.
        </div>
      </div>
    </section>
  )
`

if (!s.includes('const renderReels = () =>')) {
  const marker = '  const renderReports = () => ('
  if (!s.includes(marker)) throw new Error('renderReports marker not found')
  s = s.replace(marker, reelsFn + '\n' + marker)
}

if (!s.includes("if (active === 'Reels') return renderReels()")) {
  const marker = "    if (active === 'Posts') return renderPosts()"
  if (!s.includes(marker)) throw new Error('Posts navigation marker not found')
  s = s.replace(
    marker,
    marker + "\n    if (active === 'Reels') return renderReels()"
  )
}

fs.writeFileSync(file, s)
console.log('Reels page connected successfully.')
