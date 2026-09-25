import { Handle, Position } from '@xyflow/react'
import { NODE_TYPES } from './nodeTypes.js'

const STATUS_TEXT = {
  PENDING: 'Waiting',
  RUNNING: 'Running',
  SUCCESS: 'Done',
  FAILED: 'Failed',
  SKIPPED: 'Skipped',
}

function summary(run) {
  const parts = [STATUS_TEXT[run.status] ?? run.status]
  if (run.status === 'SUCCESS' && run.durationMs != null) parts[0] = `Done in ${run.durationMs} ms`
  if (run.attempts > 1) parts.push(`${run.attempts} attempts`)
  if (run.iterations > 1) parts.push(`${run.iterations} loops`)
  if (run.branch) parts.push(`took ${run.branch}`)
  return parts.join(', ')
}

export default function FlowNode({ id, data, selected }) {
  const def = NODE_TYPES[data.kind]
  const run = data.run
  const status = run ? run.status.toLowerCase() : 'idle'
  const preview = run?.status === 'SUCCESS' ? run.output : run?.message

  return (
    <div className={`flow-node status-${status}${selected ? ' selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="flow-node-body">
        <div className="flow-node-title">{def?.label ?? data.kind}</div>
        <div className="flow-node-id">{id}</div>
        {run && <div className="flow-node-status">{summary(run)}</div>}
        {preview && (
          <div className="flow-node-preview" title={preview}>
            {preview.length > 70 ? preview.slice(0, 70) + '…' : preview}
          </div>
        )}
      </div>
      {def?.routes ? (
        <>
          <Handle type="source" id="true" position={Position.Right} style={{ top: '30%' }} />
          <Handle type="source" id="false" position={Position.Right} style={{ top: '70%' }} />
          <span className="route-label" style={{ top: '30%' }}>true</span>
          <span className="route-label" style={{ top: '70%' }}>false</span>
        </>
      ) : (
        <Handle type="source" position={Position.Right} />
      )}
    </div>
  )
}
