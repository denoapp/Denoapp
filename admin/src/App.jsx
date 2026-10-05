import { useState, useEffect } from 'react'
import './App.css'
import { getStoredAuth, loginWithEmailPassword, logout } from './api/auth.js'
import {
  fetchScholarApplications,
  fetchAdminRole,
  decideScholarApplication,
  fetchUsers,
  updateUserStatus,
  fetchReports,
  actionReport,
  fetchPosts,
  fetchReels,
  fetchStories,
  fetchGroups,
  fetchFlaggedMessages,
  fetchAdmins,
  addAdmin,
  removeAdmin,
  fetchAuditLogs,
} from './api/adminApi.js'

const menu = [
  ['Dashboard', '▦'],
  ['Admin Profile', '●'],
  ['Users', '♙'],
  ['Posts', '▤'],
  ['Reels', '▶'],
  ['Stories', '◉'],
  ['Reports', '⚑'],
  ['Scholar Verification', '✓'],
  ['Creator Verification', '◆'],
  ['Groups', '◎'],
  ['Messages', '✉'],
  ['Notifications', '♢'],
  ['Appeals', '↗'],
  ['Moderation', '◈'],
  ['Storage', '□'],
  ['Admins & Roles', '♟'],
  ['Audit Logs', '≡'],
  ['Settings', '⚙'],
]

const pageInfo = {
  Posts: ['Posts', 'Manage posts, comments, reports and moderation actions.'],
  Reels: ['Reels', 'Review DENO video reels and moderation status.'],
  Stories: ['Stories', 'Review active stories, reports and expiry status.'],
  Notifications: ['Notifications', 'Manage user, scholar and system notifications.'],
  Appeals: ['Appeals', 'Review account, content and verification appeals.'],
  Moderation: ['Moderation', 'Manage moderation queues, warnings, suspensions and bans.'],
  Storage: ['Storage', 'Monitor media, documents, cleanup and storage usage.'],
  'Admins & Roles': ['Admins & Roles', 'Manage administrator accounts and server-side permissions.'],
  'Audit Logs': ['Audit Logs', 'Review administrative actions and security history.'],
  Settings: ['Settings', 'Configure DENO feature flags, maintenance and app settings.'],
}

function EmptyTable({ title = 'No live data yet', text = 'Real data will appear after the DENO backend is connected.' }) {
  return (
    <div className="empty-state">
      <strong>{title}</strong>
      <span>{text}</span>
    </div>
  )
}

function SummaryCards({ items }) {
  return (
    <div className="summary-grid">
      {items.map(([label, value]) => (
        <div className="summary-card" key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </div>
      ))}
    </div>
  )
}

function GenericPage({ title, description }) {
  return (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">DENO ADMIN</p>
            <h1>{title}</h1>
            <p className="muted">{description}</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>
        <EmptyTable />
      </div>
    </section>
  )
}

function App() {
  const [auth, setAuth] = useState(() => getStoredAuth())
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [active, setActive] = useState('Dashboard')
  const [mobileOpen, setMobileOpen] = useState(false)

  // Scholar verification. Kept separate from the other screens on purpose: those
  // still have no backend route, and borrowing their state would suggest they are
  // connected too.
  const [scholar, setScholar] = useState({ applications: [], counts: null, loading: true, error: '' })
  const [scholarRole, setScholarRole] = useState(null)
  const [scholarQuery, setScholarQuery] = useState('')
  const [scholarStatus, setScholarStatus] = useState('ALL')
  const [scholarType, setScholarType] = useState('ALL')
  const [scholarBusy, setScholarBusy] = useState('')
  const [scholarNotice, setScholarNotice] = useState('')

  const loadScholars = async () => {
    setScholar((prev) => ({ ...prev, loading: true, error: '' }))
    try {
      const [list, me] = await Promise.all([
        fetchScholarApplications(),
        fetchAdminRole(),
      ])
      setScholar({ applications: list.applications || [], counts: list.counts || null, loading: false, error: '' })
      setScholarRole(me)
    } catch (err) {
      setScholar({ applications: [], counts: null, loading: false, error: err.message || 'Could not load applications' })
    }
  }

  // Loaded on entry and on every return to the page, so a decision made in
  // another tab shows up instead of a stale table.
  useEffect(() => {
    if (auth && active === 'Scholar Verification') loadScholars()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth, active])

  const decideScholar = async (uid, decision) => {
    setScholarBusy(uid)
    setScholarNotice('')
    try {
      await decideScholarApplication(uid, { decision })
      const verb = decision === 'approved' ? 'Approved' : decision === 'rejected' ? 'Rejected' : 'Suspended'
      setScholarNotice(`Application ${verb.toLowerCase()}.`)
      await loadScholars()
    } catch (err) {
      setScholarNotice(err.message || 'The decision could not be recorded')
    } finally {
      setScholarBusy('')
    }
  }

  const handleLogin = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    try {
      const data = await loginWithEmailPassword(email, password)
      setAuth(data)
    } catch (err) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }

  const handleLogout = () => {
    logout()
    setAuth(null)
  }

  if (!auth) {
    return (
      <div className="admin-login-page">
        <div className="admin-login-card">
          <div className="admin-login-brand">
            <div className="brand-mark">D</div>
            <div>
              <strong>DENO</strong>
              <span>Admin Panel</span>
            </div>
          </div>

          <div className="admin-login-heading">
            <p className="eyebrow">SECURE ADMIN ACCESS</p>
            <h1>Admin Login</h1>
            <p>Sign in with your Firebase admin credentials.</p>
          </div>

          {error && <div className="error-banner" style={{ color: '#ff4d4f', marginBottom: '16px', fontSize: '13px' }}>{error}</div>}

          <form
            className="admin-login-form"
            onSubmit={handleLogin}
          >
            <label>
              Admin Email
              <input
                type="email"
                placeholder="admin@deno.app"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </label>

            <label>
              Password
              <input
                type="password"
                placeholder="Enter password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </label>

            <button type="submit" className="login-button" disabled={loading}>
              {loading ? 'Signing In...' : 'Sign In'}
            </button>
          </form>

          <div className="login-security-note">
            <strong>Firebase Authentication</strong>
            <span>Verified against staging project credentials.</span>
          </div>
        </div>
      </div>
    )
  }


  const renderAdminProfile = () => (
    <main className="page-content">
      <div className="page-header">
        <div>
          <p className="eyebrow">ADMIN ACCOUNT</p>
          <h1>Admin Profile</h1>
          <p>Manage your administrator profile and account security.</p>
        </div>
      </div>

      <div className="profile-grid">
        <section className="admin-panel-card">
          <div className="profile-avatar">D</div>
          <h2>Administrator</h2>
          <p className="muted">Super Admin</p>
          <span className="status-badge status-active">ACTIVE</span>
        </section>

        <section className="admin-panel-card">
          <h2>Profile Information</h2>

          <div className="profile-form">
            <label>
              Full Name
              <input type="text" defaultValue="Administrator" />
            </label>

            <label>
              Admin Email
              <input type="email" defaultValue="admin@deno.app" />
            </label>

            <label>
              Role
              <input type="text" value="Super Admin" readOnly />
            </label>

            <button className="primary-action" type="button">
              Save Profile
            </button>
          </div>
        </section>

        <section className="admin-panel-card">
          <h2>Security</h2>

          <div className="security-row">
            <div>
              <strong>Password</strong>
              <span>Change your administrator password.</span>
            </div>
            <button className="secondary-action" type="button">Change Password</button>
          </div>

          <div className="security-row">
            <div>
              <strong>Two-Factor Authentication</strong>
              <span>Protect administrator access with 2FA.</span>
            </div>
            <span className="status-badge status-pending">NOT CONNECTED</span>
          </div>

          <div className="security-row">
            <div>
              <strong>Active Sessions</strong>
              <span>Review and revoke administrator sessions.</span>
            </div>
            <button className="secondary-action" type="button">View Sessions</button>
          </div>
        </section>

        <section className="admin-panel-card">
          <h2>Backend Security</h2>
          <p className="muted">
            Password verification, admin roles, 2FA, session control and permissions
            will be enforced by the backend.
          </p>
          <div className="security-note">
            Real administrator credentials will never be stored in the frontend.
          </div>
        </section>
      </div>
    </main>
  )

  const openPage = (page) => {
    setActive(page)
    setMobileOpen(false)
  }

  const renderDashboard = () => (
    <section className="content">
      <div className="welcome">
        <div>
          <p className="eyebrow">DENO ADMIN PANEL</p>
          <h1>Dashboard</h1>
          <p>Central administration for the DENO Islamic social platform.</p>
        </div>
        <span className="status-pill">Backend not connected</span>
      </div>

      <SummaryCards items={[
        ['Users', '—'],
        ['Posts', '—'],
        ['Reels', '—'],
        ['Reports', '—'],
        ['Scholars', '—'],
        ['Storage', '—'],
      ]} />

      <div className="dashboard-grid">
        <div className="panel">
          <div className="panel-head">
            <div>
              <h3>Quick Actions</h3>
              <p>Common admin areas</p>
            </div>
          </div>
          <div className="quick-actions">
            <button onClick={() => openPage('Users')}>Manage Users <span>→</span></button>
            <button onClick={() => openPage('Reports')}>Review Reports <span>→</span></button>
            <button onClick={() => openPage('Scholar Verification')}>Scholar Verification <span>→</span></button>
            <button onClick={() => openPage('Moderation')}>Moderation Queue <span>→</span></button>
          </div>
        </div>

        <div className="panel">
          <div className="panel-head">
            <div>
              <h3>System Status</h3>
              <p>Production services</p>
            </div>
          </div>
          <div className="system-list">
            <div><span>Authentication</span><b>Not connected</b></div>
            <div><span>Database</span><b>Not connected</b></div>
            <div><span>Storage</span><b>Not connected</b></div>
            <div><span>Notifications</span><b>Not connected</b></div>
          </div>
        </div>
      </div>
    </section>
  )

  const renderUsers = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">USER MANAGEMENT</p>
            <h1>Users</h1>
            <p className="muted">Manage accounts, profiles, status, privacy and verification.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <SummaryCards items={[
          ['Total Users', '—'],
          ['Active', '—'],
          ['Suspended', '—'],
          ['Verified', '—'],
        ]} />

        <div className="toolbar">
          <input placeholder="Search username or email..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Active</option>
            <option>Suspended</option>
            <option>Banned</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Verification</option>
            <option>Verified</option>
            <option>Unverified</option>
          </select>
        </div>

        <EmptyTable title="No live users yet" />
      </div>
    </section>
  )


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

const renderScholarVerification = () => {
    const counts = scholar.counts || {}
    const rows = scholar.applications.filter((application) => {
      const term = scholarQuery.trim().toLowerCase()
      const matchesTerm = !term ||
        [application.fullName, application.username, application.uid, application.institution]
          .filter(Boolean)
          .some((value) => String(value).toLowerCase().includes(term))
      const matchesStatus = scholarStatus === 'ALL' || application.status === scholarStatus
      const matchesType = scholarType === 'ALL' || application.scholarType === scholarType
      return matchesTerm && matchesStatus && matchesType
    })
    const scholarTypes = [...new Set(scholar.applications.map((a) => a.scholarType).filter(Boolean))]
    // Suspend lifts a badge, so the backend refuses it to a moderator. The button
    // is hidden rather than shown-and-failing, and the backend still decides.
    const maySuspend = scholarRole?.isAdmin === true
    const pending = rows.filter((application) => application.status === 'pending')

    return (
      <section className="content">
        <div className="page-card">
          <div className="page-heading">
            <div>
              <p className="eyebrow">VERIFICATION</p>
              <h1>Scholar Verification</h1>
              <p className="muted">Review scholar applications and private qualification documents.</p>
            </div>
            <div className="heading-actions">
              <span className="backend-badge">
                {scholar.loading ? 'Loading…' : 'Staging backend'}
              </span>
              <button className="btn" onClick={loadScholars} disabled={scholar.loading}>
                Refresh
              </button>
            </div>
          </div>

          {scholar.error && (
            <div className="alert error">
              {scholar.error}
              {' '}
              <button className="link" onClick={loadScholars}>Retry</button>
            </div>
          )}
          {scholarNotice && <div className="alert">{scholarNotice}</div>}

          <SummaryCards items={[
            ['Pending', counts.pending ?? 0],
            ['Verified', counts.approved ?? 0],
            ['Rejected', counts.rejected ?? 0],
            ['Suspended', counts.suspended ?? 0],
          ]} />

          <div className="toolbar">
            <input
              placeholder="Search scholar or username..."
              value={scholarQuery}
              onChange={(e) => setScholarQuery(e.target.value)}
            />
            <select value={scholarStatus} onChange={(e) => setScholarStatus(e.target.value)}>
              <option value="ALL">All Status</option>
              <option value="pending">Pending</option>
              <option value="approved">Verified</option>
              <option value="rejected">Rejected</option>
              <option value="suspended">Suspended</option>
            </select>
            <select value={scholarType} onChange={(e) => setScholarType(e.target.value)}>
              <option value="ALL">All Scholar Types</option>
              {scholarTypes.map((type) => (
                <option key={type} value={type}>{type}</option>
              ))}
            </select>
          </div>

          {scholar.loading ? (
            <div className="empty-state">
              <strong>Loading applications…</strong>
              <span>Reading verificationApplications from the staging project.</span>
            </div>
          ) : rows.length === 0 ? (
            <EmptyTable
              title={scholar.applications.length === 0
                ? 'No live scholar applications'
                : 'Nothing matches these filters'}
              text={scholar.applications.length === 0
                ? 'Applications appear here as soon as somebody submits one.'
                : 'Clear the search or filters to see the rest of the queue.'}
            />
          ) : (
            <div className="table-wrap">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Applicant</th>
                    <th>Type</th>
                    <th>Institution</th>
                    <th>Country</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((application) => (
                    <tr key={application.uid}>
                      <td>
                        <strong>{application.fullName || '—'}</strong>
                        <span className="sub">{application.username ? '@' + application.username : application.uid}</span>
                      </td>
                      <td>{application.scholarType || '—'}</td>
                      <td>{application.institution || '—'}</td>
                      <td>{application.country || '—'}</td>
                      <td>
                        <span className={'pill pill-' + (application.status || 'unknown')}>
                          {application.status || 'unknown'}
                        </span>
                      </td>
                      <td className="actions">
                        {application.status === 'pending' ? (
                          <>
                            <button
                              className="btn primary"
                              disabled={scholarBusy === application.uid}
                              onClick={() => decideScholar(application.uid, 'approved')}
                            >
                              Approve
                            </button>
                            <button
                              className="btn danger"
                              disabled={scholarBusy === application.uid}
                              onClick={() => decideScholar(application.uid, 'rejected')}
                            >
                              Reject
                            </button>
                          </>
                        ) : (
                          <span className="muted">
                            {application.reviewedBy ? 'by ' + application.reviewedBy : 'decided'}
                          </span>
                        )}
                        {maySuspend && application.status === 'approved' && (
                          <button
                            className="btn"
                            disabled={scholarBusy === application.uid}
                            onClick={() => decideScholar(application.uid, 'suspended')}
                          >
                            Suspend
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="info-box">
            <strong>Verification workflow</strong>
            <p>Application → Document Review → Decision → Verified / Rejected / Suspended</p>
            <p>
              {pending.length} pending in view. Applicant email, phone and uploaded document
              locations are deliberately not returned to this panel; open the application from
              the app to read them.
            </p>
          </div>
        </div>
      </section>
    )
  }

  const renderCreatorVerification = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">VERIFICATION</p>
            <h1>Creator Verification</h1>
            <p className="muted">Review creator verification applications and badge status.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <SummaryCards items={[
          ['Pending', '—'],
          ['Verified', '—'],
          ['Rejected', '—'],
          ['Suspended', '—'],
        ]} />

        <div className="toolbar">
          <input placeholder="Search creator..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Pending</option>
            <option>Verified</option>
            <option>Rejected</option>
            <option>Suspended</option>
          </select>
        </div>

        <EmptyTable title="No live creator applications" />
        <div className="info-box">
          <strong>Creator badge</strong>
          <p>Admin-controlled verification. A creator badge does not mean endorsement of every statement or post.</p>
        </div>
      </div>
    </section>
  )

  const renderGroups = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">COMMUNITY MANAGEMENT</p>
            <h1>Groups</h1>
            <p className="muted">Manage DENO groups, members, privacy and reported communities.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <SummaryCards items={[
          ['Total Groups', '—'],
          ['Active', '—'],
          ['Reported', '—'],
          ['Suspended', '—'],
        ]} />

        <div className="toolbar">
          <input placeholder="Search group name or owner..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Active</option>
            <option>Reported</option>
            <option>Suspended</option>
          </select>
          <select defaultValue="ALL">
            <option value="ALL">All Privacy</option>
            <option>Public</option>
            <option>Private</option>
          </select>
        </div>

        <EmptyTable title="No live groups" />
        <div className="info-box">
          <strong>Group management</strong>
          <p>Admins can review reported groups, inspect details and suspend or restore communities when authorized.</p>
        </div>
      </div>
    </section>
  )

  const renderMessages = () => (
    <section className="content">
      <div className="page-card">
        <div className="page-heading">
          <div>
            <p className="eyebrow">MESSAGING SAFETY</p>
            <h1>Messages</h1>
            <p className="muted">Only reported or flagged conversations should become reviewable.</p>
          </div>
          <span className="backend-badge">Backend not connected</span>
        </div>

        <SummaryCards items={[
          ['Flagged', '—'],
          ['Under Review', '—'],
          ['Resolved', '—'],
        ]} />

        <div className="toolbar">
          <input placeholder="Search conversations..." />
          <select defaultValue="ALL">
            <option value="ALL">All Status</option>
            <option>Flagged</option>
            <option>Under Review</option>
            <option>Resolved</option>
          </select>
        </div>

        <EmptyTable title="No flagged conversations yet" text="Private chats are not broadly browsable by Admin." />
      </div>
    </section>
  )

  const renderGeneric = (page) => {
    const [title, description] = pageInfo[page]
    return <GenericPage title={title} description={description} />
  }

  const renderPage = () => {
    if (active === 'Admin Profile') return renderAdminProfile()
    if (active === 'Dashboard') return renderDashboard()
    if (active === 'Users') return renderUsers()
    if (active === 'Posts') return renderPosts()
    if (active === 'Reels') return renderReels()
    if (active === 'Stories') return renderStories()
    if (active === 'Reports') return renderReports()
    if (active === 'Scholar Verification') return renderScholarVerification()
    if (active === 'Creator Verification') return renderCreatorVerification()
    if (active === 'Groups') return renderGroups()
    if (active === 'Messages') return renderMessages()
    return renderGeneric(active)
  }

  return (
    <div className="admin-shell">
      <aside className={`sidebar ${mobileOpen ? 'open' : ''}`}>
        <div className="brand">
          <div className="brand-mark">D</div>
          <div>
            <strong>DENO</strong>
            <span>Admin Panel</span>
          </div>
        </div>

        <nav className="nav">
          {menu.map(([label, icon]) => (
            <button
              key={label}
              className={`nav-item ${active === label ? 'active' : ''}`}
              onClick={() => openPage(label)}
            >
              <span className="nav-icon">{icon}</span>
              <span>{label}</span>
            </button>
          ))}
        </nav>

        <div className="profile-mini" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div className="admin-avatar">A</div>
            <div>
              <strong>Administrator</strong>
              <span style={{ fontSize: '11px', display: 'block', opacity: 0.7 }}>{auth?.email || 'Super Admin'}</span>
            </div>
          </div>
          <button
            onClick={handleLogout}
            title="Sign Out"
            style={{
              background: 'transparent',
              border: '1px solid rgba(255,255,255,0.2)',
              color: '#fff',
              padding: '4px 8px',
              borderRadius: '4px',
              cursor: 'pointer',
              fontSize: '12px',
            }}
          >
            Logout
          </button>
        </div>
      </aside>

      {mobileOpen && (
        <button
          className="mobile-backdrop"
          aria-label="Close menu"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <main className="main">
        <header className="topbar">
          <div className="topbar-left">
            <button className="menu-button" onClick={() => setMobileOpen(true)} aria-label="Open menu">☰</button>
            <div>
              <strong>{active}</strong>
              <span>DENO Administration</span>
            </div>
          </div>
          <div className="topbar-right">
          </div>
        </header>

        {renderPage()}
      </main>
    </div>
  )
}

export default App
