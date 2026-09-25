import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  Background,
  Controls,
  MarkerType,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import FlowNode from './FlowNode.jsx'
import { NODE_TYPES } from './nodeTypes.js'
import { EXAMPLES } from './examples.js'
import './styles.css'

const nodeTypes = { flow: FlowNode }
const arrow = { type: MarkerType.ArrowClosed }
let idCounter = 1

function toFlowNode({ id, kind, x, y, config, maxRetries, backoffMs, loop }) {
  return {
    id: id ?? `${kind.toLowerCase()}_${idCounter++}`,
    type: 'flow',
    position: { x, y },
    data: {
      kind,
      config: { ...NODE_TYPES[kind].defaults, ...config },
      maxRetries: maxRetries ?? 0,
      backoffMs: backoffMs ?? 0,
      loop: loop ?? null,
    },
  }
}

function toFlowEdge({ source, target, label }) {
  return {
    id: `${source}->${target}${label ? `:${label}` : ''}`,
    source,
    target,
    sourceHandle: label || undefined,
    label: label || undefined,
    markerEnd: arrow,
  }
}

export default function App() {
  const [nodes, setNodes, onNodesChange] = useNodesState([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [name, setName] = useState('My workflow')
  const [selectedId, setSelectedId] = useState(null)
  const [run, setRun] = useState(null)
  const [error, setError] = useState(null)
  const timer = useRef(null)

  useEffect(() => () => clearInterval(timer.current), [])

  const selected = nodes.find((n) => n.id === selectedId) ?? null
  const running = run?.status === 'RUNNING'

  // Overlay live run results onto the editor nodes without touching the editable state.
  const shownNodes = useMemo(
    () => nodes.map((n) => ({ ...n, data: { ...n.data, run: run?.nodes?.[n.id] } })),
    [nodes, run],
  )

  const onConnect = useCallback(
    (params) =>
      setEdges((eds) =>
        addEdge({ ...params, label: params.sourceHandle || undefined, markerEnd: arrow }, eds),
      ),
    [setEdges],
  )

  function addNode(kind) {
    const offset = (nodes.length % 6) * 36
    setNodes((ns) => [...ns, toFlowNode({ kind, x: 80 + offset, y: 60 + offset })])
  }

  function loadExample(key) {
    const ex = EXAMPLES[key]
    if (!ex) return
    clearInterval(timer.current)
    setRun(null)
    setError(null)
    setSelectedId(null)
    setName(ex.name)
    setNodes(ex.nodes.map(toFlowNode))
    setEdges(ex.edges.map(toFlowEdge))
  }

  function patchSelected(patch) {
    setNodes((ns) => ns.map((n) => (n.id === selectedId ? { ...n, data: { ...n.data, ...patch } } : n)))
  }

  function patchConfig(key, value) {
    patchSelected({ config: { ...selected.data.config, [key]: value } })
  }

  function deleteSelected() {
    setNodes((ns) => ns.filter((n) => n.id !== selectedId))
    setEdges((es) => es.filter((e) => e.source !== selectedId && e.target !== selectedId))
    setSelectedId(null)
  }

  function toSpec() {
    return {
      name,
      nodes: nodes.map((n) => ({
        id: n.id,
        type: n.data.kind,
        config: n.data.config,
        maxRetries: Number(n.data.maxRetries) || 0,
        backoffMs: Number(n.data.backoffMs) || 0,
        loop: n.data.loop
          ? { maxIterations: Number(n.data.loop.maxIterations) || 1, untilContains: n.data.loop.untilContains || null }
          : null,
      })),
      edges: edges.map((e) => ({ source: e.source, target: e.target, label: e.label || null })),
    }
  }

  function poll(runId) {
    clearInterval(timer.current)
    const tick = async () => {
      try {
        const res = await fetch(`/api/runs/${runId}`)
        const view = await res.json()
        setRun(view)
        if (view.status !== 'RUNNING') clearInterval(timer.current)
      } catch {
        clearInterval(timer.current)
        setError('Lost the connection to the backend while the run was in progress.')
      }
    }
    tick()
    timer.current = setInterval(tick, 300)
  }

  async function startRun() {
    setError(null)
    setRun(null)
    try {
      const res = await fetch('/api/runs', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(toSpec()),
      })
      const body = await res.json().catch(() => ({}))
      if (!res.ok) {
        setError(body.error ?? `The backend returned status ${res.status}.`)
        return
      }
      poll(body.runId)
    } catch {
      setError('Could not reach the backend on port 8080. Start it with "mvn spring-boot:run".')
    }
  }

  const selectedRun = selected ? run?.nodes?.[selected.id] : null
  const def = selected ? NODE_TYPES[selected.data.kind] : null

  return (
    <div className="app">
      <header className="topbar">
        <h1>Flownode</h1>
        <input
          className="name-input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-label="Workflow name"
        />
        <select
          className="example-select"
          value=""
          onChange={(e) => loadExample(e.target.value)}
          aria-label="Load an example workflow"
        >
          <option value="">Load an example…</option>
          {Object.keys(EXAMPLES).map((k) => (
            <option key={k} value={k}>{k}</option>
          ))}
        </select>
        <div className="spacer" />
        {run && (
          <span className={`pill pill-${run.status.toLowerCase()}`} role="status">
            {run.status === 'RUNNING' && 'Running…'}
            {run.status === 'SUCCESS' && `Finished in ${run.durationMs} ms`}
            {run.status === 'FAILED' && `Finished with failures in ${run.durationMs} ms`}
          </span>
        )}
        <button className="primary" onClick={startRun} disabled={running || nodes.length === 0}>
          Run workflow
        </button>
      </header>

      {error && <div className="banner" role="alert">{error}</div>}

      <aside className="palette">
        <h2>Add a node</h2>
        {Object.entries(NODE_TYPES).map(([kind, d]) => (
          <button key={kind} className="palette-item" onClick={() => addNode(kind)}>
            <span className="palette-name">{d.label}</span>
            <span className="palette-blurb">{d.blurb}</span>
          </button>
        ))}
      </aside>

      <main className="canvas">
        {nodes.length === 0 && (
          <div className="empty">
            <p>Add nodes from the left, drag from one node&apos;s right handle to another&apos;s left handle to connect them.</p>
            <p>Or pick an example from the top bar.</p>
          </div>
        )}
        <ReactFlow
          nodes={shownNodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeClick={(_, n) => setSelectedId(n.id)}
          onPaneClick={() => setSelectedId(null)}
          fitView
          fitViewOptions={{ padding: 0.25 }}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={22} size={1.2} color="#B9C3CF" />
          <Controls showInteractive={false} />
        </ReactFlow>
      </main>

      <aside className="inspector">
        {!selected && <p className="hint">Select a node to edit it. Its result shows here after a run.</p>}
        {selected && (
          <>
            <h2>{def.label} <span className="inspector-id">{selected.id}</span></h2>
            {def.fields.map((f) => (
              <label key={f.key} className="field">
                <span>{f.label}</span>
                {f.multiline ? (
                  <textarea
                    rows={4}
                    value={selected.data.config[f.key] ?? ''}
                    onChange={(e) => patchConfig(f.key, e.target.value)}
                  />
                ) : (
                  <input
                    value={selected.data.config[f.key] ?? ''}
                    onChange={(e) => patchConfig(f.key, e.target.value)}
                  />
                )}
              </label>
            ))}

            <h3>If it fails</h3>
            <div className="row">
              <label className="field">
                <span>Retries</span>
                <input
                  type="number"
                  min="0"
                  value={selected.data.maxRetries}
                  onChange={(e) => patchSelected({ maxRetries: e.target.value })}
                />
              </label>
              <label className="field">
                <span>Wait before retry (ms)</span>
                <input
                  type="number"
                  min="0"
                  value={selected.data.backoffMs}
                  onChange={(e) => patchSelected({ backoffMs: e.target.value })}
                />
              </label>
            </div>
            <p className="hint">The wait doubles after each further failure.</p>

            <h3>Repeat</h3>
            <label className="check">
              <input
                type="checkbox"
                checked={!!selected.data.loop}
                onChange={(e) =>
                  patchSelected({ loop: e.target.checked ? { maxIterations: 3, untilContains: '' } : null })
                }
              />
              Run this node more than once
            </label>
            {selected.data.loop && (
              <div className="row">
                <label className="field">
                  <span>At most</span>
                  <input
                    type="number"
                    min="1"
                    value={selected.data.loop.maxIterations}
                    onChange={(e) => patchSelected({ loop: { ...selected.data.loop, maxIterations: e.target.value } })}
                  />
                </label>
                <label className="field">
                  <span>Stop when output contains</span>
                  <input
                    value={selected.data.loop.untilContains}
                    onChange={(e) => patchSelected({ loop: { ...selected.data.loop, untilContains: e.target.value } })}
                  />
                </label>
              </div>
            )}

            {selectedRun && (
              <>
                <h3>Last run</h3>
                <dl className="result">
                  <dt>Status</dt>
                  <dd>{selectedRun.status.toLowerCase()}</dd>
                  {selectedRun.durationMs != null && (<><dt>Time</dt><dd>{selectedRun.durationMs} ms</dd></>)}
                  <dt>Attempts</dt>
                  <dd>{selectedRun.attempts}</dd>
                  {selectedRun.iterations > 1 && (<><dt>Loops</dt><dd>{selectedRun.iterations}</dd></>)}
                  {selectedRun.branch && (<><dt>Branch</dt><dd>{selectedRun.branch}</dd></>)}
                </dl>
                {selectedRun.message && <pre className="output error-text">{selectedRun.message}</pre>}
                {selectedRun.output && <pre className="output">{selectedRun.output}</pre>}
              </>
            )}

            <button className="danger" onClick={deleteSelected}>Delete node</button>
          </>
        )}
      </aside>
    </div>
  )
}
