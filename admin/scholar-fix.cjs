const fs = require('fs')

const file = 'src/App.jsx'
let s = fs.readFileSync(file, 'utf8')

const scholarFn = `
  const renderScholarVerification = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">VERIFICATION</p>
            <h1>Scholar Verification</h1>
            <p className="muted">Review scholar applications, qualifications and private verification documents.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <div className="summary-grid">
          <div className="summary-card"><span>Total Applications</span><strong>—</strong></div>
          <div className="summary-card"><span>Pending</span><strong>—</strong></div>
          <div className="summary-card"><span>Verified</span><strong>—</strong></div>
          <div className="summary-card"><span>Rejected / Suspended</span><strong>—</strong></div>
        </div>

        <div className="toolbar">
          <input placeholder="Search name, username or application ID..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Pending</option>
            <option>Verified</option>
            <option>Rejected</option>
            <option>Suspended</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Scholar Types</option>
            <option>Aalim</option>
            <option>Mufti</option>
            <option>Hafiz</option>
            <option>Qari</option>
            <option>Islamic Teacher</option>
            <option>Khatib</option>
            <option>Muhaddith</option>
            <option>Mufassir</option>
            <option>Arabic Scholar</option>
            <option>Other</option>
          </select>
        </div>

        <div className="posts-table-wrap">
          <table className="posts-table">
            <thead>
              <tr>
                <th>APPLICANT</th>
                <th>SCHOLAR TYPE</th>
                <th>QUALIFICATION</th>
                <th>DOCUMENTS</th>
                <th>STATUS</th>
                <th>SUBMITTED</th>
                <th>ACTION</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <div className="post-placeholder">
                    <strong>No live applications</strong>
                    <span>Backend connection required</span>
                  </div>
                </td>
                <td>—</td>
                <td>—</td>
                <td><span className="status-badge">Private</span></td>
                <td><span className="status-badge">—</span></td>
                <td>—</td>
                <td><button className="view-button" disabled>Review</button></td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="info-box">
          <strong>Scholar verification workflow</strong>
          <p>Application → Document Review → Approve / Reject → Verified Badge. Only approved scholars should appear in the DENO scholar directory.</p>
        </div>

        <div className="info-box">
          <strong>Private document protection</strong>
          <p>Certificates, degrees, ijazah and supporting documents must remain private and accessible only to authorized verification staff. Access should be logged by the backend.</p>
        </div>

        <div className="demo-notice">
          No fake scholar applications or certificates are being shown. Real applications will be loaded from the DENO backend.
        </div>
      </div>
    </section>
  )
`

const startMarker = '  const renderScholarVerification = () => ('
const endMarker = '  const renderAskMasla = () => ('

const start = s.indexOf(startMarker)
const end = s.indexOf(endMarker)

if (start === -1) throw new Error('Scholar Verification function not found')
if (end === -1) throw new Error('Ask a Masla marker not found')

s = s.slice(0, start) + scholarFn + '\n\n' + s.slice(end)

if (!s.includes("if (active === 'Scholar Verification') return renderScholarVerification()")) {
  const fallback = "    if (active === 'Reports') return renderReports()"
  if (!s.includes(fallback)) throw new Error('Reports navigation marker not found')

  s = s.replace(
    fallback,
    fallback + "\n    if (active === 'Scholar Verification') return renderScholarVerification()"
  )
}

fs.writeFileSync(file, s)
console.log('Scholar Verification page updated successfully.')
