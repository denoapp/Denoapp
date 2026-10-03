const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

const postsFn = `
  const renderPosts = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">CONTENT MANAGEMENT</p>
            <h1>Posts</h1>
            <p className="muted">Review DENO posts, visibility, reports and moderation actions.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <div className="summary-grid">
          <div className="summary-card"><span>Total Posts</span><strong>—</strong></div>
          <div className="summary-card"><span>Published</span><strong>—</strong></div>
          <div className="summary-card"><span>Reported</span><strong>—</strong></div>
          <div className="summary-card"><span>Removed</span><strong>—</strong></div>
        </div>

        <div className="toolbar">
          <input placeholder="Search post, username or ID..." />
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
            <option value="ALL">All Types</option>
            <option>Text</option>
            <option>Photo</option>
            <option>Video</option>
            <option>Document</option>
          </select>
        </div>

        <div className="posts-table-wrap">
          <table className="posts-table">
            <thead>
              <tr>
                <th>POST</th>
                <th>AUTHOR</th>
                <th>TYPE</th>
                <th>VISIBILITY</th>
                <th>STATUS</th>
                <th>REPORTS</th>
                <th>ACTION</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><div className="post-placeholder"><strong>No live posts</strong><span>Backend connection required</span></div></td>
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
          <strong>Post moderation workflow</strong>
          <p>Published → Reported → Review → Restore / Remove.</p>
        </div>

        <div className="demo-notice">
          No fake post data is being shown. Real posts will be loaded from the DENO backend.
        </div>
      </div>
    </section>
  )
`

if (!s.includes('const renderPosts = () =>')) {
  const marker = '  const renderReports = () => ('
  if (!s.includes(marker)) throw new Error('renderReports marker not found')
  s = s.replace(marker, postsFn + '\n' + marker)
}

if (!s.includes("if (active === 'Posts') return renderPosts()")) {
  const marker = "    if (active === 'Reports') return renderReports()"
  if (!s.includes(marker)) throw new Error('renderReports navigation marker not found')
  s = s.replace(marker, "    if (active === 'Posts') return renderPosts()\n" + marker)
}

fs.writeFileSync(file, s)
console.log('Posts page connected successfully.')
