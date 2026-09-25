// Ready-made workflows. Coordinates are canvas positions.
export const EXAMPLES = {
  'Parallel branches with a retry': {
    name: 'Order checkout',
    nodes: [
      { id: 'order', kind: 'TEXT', x: 0, y: 140, config: { template: 'order #4821 received' } },
      { id: 'inventory', kind: 'DELAY', x: 280, y: 20, config: { millis: '900' } },
      { id: 'payment', kind: 'FLAKY', x: 280, y: 260, config: { failTimes: '2' }, maxRetries: 3, backoffMs: 400 },
      { id: 'merge', kind: 'TEXT', x: 560, y: 140, config: { template: 'stock: {{inventory}} | payment: {{payment}}' } },
      { id: 'check', kind: 'CONDITION', x: 840, y: 140, config: { contains: 'ok after' } },
      { id: 'ship', kind: 'TEXT', x: 1120, y: 40, config: { template: 'Shipping now. {{input}}' } },
      { id: 'hold', kind: 'TEXT', x: 1120, y: 260, config: { template: 'Held for review. {{input}}' } },
    ],
    edges: [
      { source: 'order', target: 'inventory' },
      { source: 'order', target: 'payment' },
      { source: 'inventory', target: 'merge' },
      { source: 'payment', target: 'merge' },
      { source: 'merge', target: 'check' },
      { source: 'check', target: 'ship', label: 'true' },
      { source: 'check', target: 'hold', label: 'false' },
    ],
  },
  'Loop until the output matches': {
    name: 'Loop demo',
    nodes: [
      { id: 'counter', kind: 'TEXT', x: 0, y: 80, config: { template: 'attempt {{iteration}}' }, loop: { maxIterations: 6, untilContains: 'attempt 3' } },
      { id: 'report', kind: 'TEXT', x: 300, y: 80, config: { template: 'Stopped at: {{input}}' } },
    ],
    edges: [{ source: 'counter', target: 'report' }],
  },
  'LLM answer with a yes/no check': {
    name: 'Answer review',
    nodes: [
      { id: 'question', kind: 'TEXT', x: 0, y: 120, config: { template: 'Explain in two sentences why the sky is blue.' } },
      { id: 'answer', kind: 'LLM', x: 280, y: 120, config: { prompt: '{{input}}' }, maxRetries: 1, backoffMs: 500 },
      { id: 'review', kind: 'LLM_DECISION', x: 560, y: 120, config: { prompt: 'Is this explanation scientifically accurate?\n\n{{input}}' }, maxRetries: 2, backoffMs: 500 },
      { id: 'approved', kind: 'TEXT', x: 860, y: 20, config: { template: 'APPROVED:\n{{input}}' } },
      { id: 'flagged', kind: 'TEXT', x: 860, y: 240, config: { template: 'NEEDS REVIEW:\n{{input}}' } },
    ],
    edges: [
      { source: 'question', target: 'answer' },
      { source: 'answer', target: 'review' },
      { source: 'review', target: 'approved', label: 'true' },
      { source: 'review', target: 'flagged', label: 'false' },
    ],
  },
}
