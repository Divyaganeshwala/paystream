import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-root',
  standalone: false,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {

  private apiUrl = window.location.port === '4200' 
  ? 'http://localhost:8080/api' 
  : '/api';
  private pollInterval: any;

  processors: any[] = [
    { name: 'RAZORPAY', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, consecutiveSuccesses: 0, totalHandled: 0, score: 100 },
    { name: 'PAYPAL', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, consecutiveSuccesses: 0, totalHandled: 0, score: 100 },
    { name: 'CASHFREE', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, consecutiveSuccesses: 0, totalHandled: 0, score: 100 }
  ];

  stats: any = { total: 0, success: 0, failed: 0, successRate: 0 };
  payments: any[] = [];
  events: any[] = [];
  lastRouting: any = null;
  loadTestCount: number = 50;
  loadTestRunning: boolean = false;
  loadTestResult: string = '';
  systemStatus: string = 'HEALTHY';
  threadCount: number = 1;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.pollHealth();
    this.loadStats();
    this.loadPayments();
    this.loadEvents();
    this.pollInterval = setInterval(() => {
      this.pollHealth();
      this.loadStats();
      this.loadPayments();
      this.loadEvents();
    }, 5000);
  }

  ngOnDestroy() {
    clearInterval(this.pollInterval);
  }

  pollHealth() {
    this.http.get(`${this.apiUrl}/health`, { responseType: 'text' }).subscribe(data => {
      this.parseHealth(data);
      this.updateSystemStatus();
    });
  }

  parseHealth(data: string) {
    const lines = data.trim().split('\n');
    lines.forEach(line => {
      const name = line.split('→')[0].trim();
      const processor = this.processors.find(p => p.name === name);
      if (processor) {
        processor.state = this.extract(line, 'state: ', ' |');
        processor.consecutiveFailures = parseInt(this.extract(line, 'consecutiveFailures: ', ' |'));
        processor.consecutiveSuccesses = parseInt(this.extract(line, 'consecutiveSuccesses: ', ' |'));
        processor.avgLatency = parseFloat(this.extract(line, 'avgLatency: ', 'ms'));
        processor.successRate = parseFloat(this.extract(line, 'successRate: ', '%'));
        processor.score = parseFloat(this.extract(line, 'score: ', ' |'));
        //processor.totalHandled = parseInt(this.extract(line, 'totalHandled: ', '\n'));
      }
    });
  }

  extract(line: string, start: string, end: string): string {
    const startIdx = line.indexOf(start) + start.length;
    const endIdx = line.indexOf(end, startIdx);
    return endIdx === -1 ? line.substring(startIdx).trim() : line.substring(startIdx, endIdx).trim();
  }

  updateSystemStatus() {
    const anyOpen = this.processors.some(p => p.state === 'OPEN');
    const anyHalfOpen = this.processors.some(p => p.state === 'HALF_OPEN');
    if (anyOpen) this.systemStatus = 'DEGRADED';
    else if (anyHalfOpen) this.systemStatus = 'RECOVERING';
    else this.systemStatus = 'HEALTHY';
  }

  getStateClass(state: string): string {
    if (state === 'CLOSED') return 'state-closed';
    if (state === 'OPEN') return 'state-open';
    return 'state-half-open';
  }

  getMaxScore(): number {
    return Math.max(...this.processors.map(p => p.score), 1);
  }

  loadStats() {
    this.http.get(`${this.apiUrl}/stats`).subscribe((data: any) => {
        this.stats = data;
        // update totalHandled on each processor card
        if (data.handledPerProcessor) {
            this.processors.forEach(p => {
                p.totalHandled = data.handledPerProcessor[p.name] ?? 0;
            });
        }
    });
  }

  loadPayments() {
    this.http.get(`${this.apiUrl}/payments`).subscribe((data: any) => {
      this.payments = data;
    });
  }

  loadEvents() {
    this.http.get(`${this.apiUrl}/events`).subscribe((data: any) => {
      this.events = data.reverse();
    });
  }

  sendPayment() {
      this.http.post(`${this.apiUrl}/payment`,
          { amount: '500', currency: 'INR' },
          { responseType: 'text' }
      ).subscribe(response => {
          this.loadLastRouting();
          this.loadStats();
          this.loadPayments();
          this.pollHealth();
          this.loadEvents();
      });
  }

  loadLastRouting() {
    this.http.get(`${this.apiUrl}/payments`).subscribe((data: any) => {
      if (data && data.length > 0) {
        const lastPayment = data[0];
        this.http.get(`${this.apiUrl}/payments/${lastPayment.id}/routing`).subscribe((routing: any) => {
          this.lastRouting = { payment: lastPayment, decisions: routing };
        });
      }
    });
  }

  simulateFailure(processorName: string) {
    this.http.post(`${this.apiUrl}/simulate/failure/${processorName}`, {},
        { responseType: 'text' }
    ).subscribe(() => {
        this.pollHealth();
        this.loadEvents();
        this.loadStats();
        this.loadPayments();
    });
  }

  runLoadTest() {
    this.loadTestRunning = true;
    this.loadTestResult = '';
    this.http.get(`${this.apiUrl}/loadtest/smart?payments=${this.loadTestCount}&threads=${this.threadCount}`,
      { responseType: 'text' }
    ).subscribe(result => {
      this.loadTestResult = result;
      this.loadTestRunning = false;
      this.loadStats();
      this.loadPayments();
      this.pollHealth();
    });
  }

  flushRedis() {
    this.http.post(`${this.apiUrl}/redis/flush`, {}, { responseType: 'text' })
      .subscribe(() => this.pollHealth());
  }
  
formatTime(dateStr: string): string {
  if (!dateStr) return '';
  // Always treat as UTC since we store UTC
  const date = new Date(dateStr + 'Z');
  return date.toLocaleTimeString('en-IN', { 
    hour: '2-digit', 
    minute: '2-digit', 
    second: '2-digit', 
    fractionalSecondDigits: 3,
    hour12: false 
  });
}
  
}

