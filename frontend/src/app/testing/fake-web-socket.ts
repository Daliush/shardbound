/** A WebSocket the test drives: it opens, receives and closes when told, and records what the client sends. */
export class FakeWebSocket {
  static readonly instances: FakeWebSocket[] = [];

  readonly sent: unknown[] = [];
  readyState = 0;
  onopen: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;

  constructor(readonly url: string) {
    FakeWebSocket.instances.push(this);
  }

  static latest(): FakeWebSocket {
    return FakeWebSocket.instances[FakeWebSocket.instances.length - 1];
  }

  static reset(): void {
    FakeWebSocket.instances.length = 0;
  }

  open(): void {
    this.readyState = 1;
    this.onopen?.(new Event('open'));
  }

  receive(message: object): void {
    this.onmessage?.({ data: JSON.stringify(message) } as MessageEvent);
  }

  /** The server or the network closes the connection. */
  drop(code = 1006, wasClean = false): void {
    this.readyState = 3;
    this.onclose?.({ code, reason: '', wasClean } as CloseEvent);
  }

  send(data: string): void {
    this.sent.push(JSON.parse(data));
  }

  close(): void {
    this.readyState = 3;
  }
}
