// What each node type looks like in the editor. The `kind` must match NodeHandler.type() on the backend.
export const NODE_TYPES = {
  TEXT: {
    label: 'Text',
    blurb: 'Builds text from a template. Use {{input}} or {{node_id}}.',
    defaults: { template: '{{input}}' },
    fields: [{ key: 'template', label: 'Template', multiline: true }],
  },
  DELAY: {
    label: 'Delay',
    blurb: 'Waits, then passes its input on. Good for seeing parallel branches.',
    defaults: { millis: '500' },
    fields: [{ key: 'millis', label: 'Wait (ms)' }],
  },
  CONDITION: {
    label: 'Condition',
    blurb: 'Sends the input down the true or false branch.',
    defaults: { contains: '' },
    fields: [{ key: 'contains', label: 'Input contains' }],
    routes: true,
  },
  FLAKY: {
    label: 'Flaky task',
    blurb: 'Fails on purpose a few times, to show retries.',
    defaults: { failTimes: '2' },
    fields: [{ key: 'failTimes', label: 'Failures before success' }],
  },
  LLM: {
    label: 'LLM prompt',
    blurb: 'Asks the local Ollama model and returns its answer.',
    defaults: { prompt: '{{input}}' },
    fields: [{ key: 'prompt', label: 'Prompt', multiline: true }],
  },
  LLM_DECISION: {
    label: 'LLM decision',
    blurb: 'Asks the model a yes/no question and routes on the answer.',
    defaults: { prompt: 'Is the following text polite?\n\n{{input}}' },
    fields: [{ key: 'prompt', label: 'Yes/no question', multiline: true }],
    routes: true,
  },
}
