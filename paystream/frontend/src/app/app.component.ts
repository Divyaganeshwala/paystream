import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-root',
  standalone: false,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {

  private apiUrl = 'http://localhost:8080/api';
  private pollInterval: any;

  currentMode = 'SMART';

  processors: any[] = [
  { name: 'RAZORPAY', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, totalHandled: 0 },
  { name: 'PAYU', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, totalHandled: 0 },
  { name: 'CASHFREE', state: 'CLOSED', successRate: 100, avgLatency: 0, consecutiveFailures: 0, totalHandled: 0 }
];

  paymentFeed: any[] = [];
  stats: any = { total: 0, success: 0, failed: 0, successRate: 0 };
  payments: any[] = [];
  events: any[] = [];
  loadTestCount: number = 50;
  constructor(private http: HttpClient) {}

  loadStats() {
  this.http.get(`${this.apiUrl}/stats`).subscribe((data: any) => {
    this.stats = data;
  });
}

loadPayments() {
  this.http.get(`${this.apiUrl}/payments`).subscribe((data: any) => {
    this.payments = data.reverse();
  });
}

loadEvents() {
  this.http.get(`${this.apiUrl}/events`).subscribe((data: any) => {
    this.events = data.reverse();
  });
}

runLoadTest() {
  this.http.get(`${this.apiUrl}/loadtest/run?payments=${this.loadTestCount}`,
    { responseType: 'text' }
  ).subscribe(result => {
    alert(result);
    this.loadStats();
    this.loadPayments();
  });
}
 

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
        processor.totalHandled = parseInt(this.extract(line, 'totalHandled: ', '\n'));
      }
    });
  }

  extract(line: string, start: string, end: string): string {
    const startIdx = line.indexOf(start) + start.length;
    const endIdx = line.indexOf(end, startIdx);
    return endIdx === -1 ? line.substring(startIdx).trim() : line.substring(startIdx, endIdx).trim();
  }

  getStateClass(state: string): string {
    if (state === 'CLOSED') return 'state-closed';
    if (state === 'OPEN') return 'state-open';
    return 'state-half-open';
  }

  sendPayment() {
    this.http.post(`${this.apiUrl}/payment`,
      { amount: '500', currency: 'INR' },
      { responseType: 'text' }
    ).subscribe(response => {
      const status = response.includes('SUCCESS') ? 'SUCCESS' : 'FAILED';
      const processor = response.split('on: ')[1]?.split(' |')[0] || '';
      const amount = response.split('Amount: ')[1] || '500';
      this.paymentFeed.unshift({
        time: new Date().toLocaleTimeString(),
        processor,
        amount,
        status,
        latency: '-'
      });
      if (this.paymentFeed.length > 20) this.paymentFeed.pop();
      this.pollHealth();
      this.loadStats();
      this.loadPayments();
    });
  }

  simulateFailure(processorName: string) {
    this.http.post(`${this.apiUrl}/simulate/failure/${processorName}`, {},
      { responseType: 'text' }
    ).subscribe(() => this.pollHealth());
  }

  toggleMode() {
    const newMode = this.currentMode === 'SMART' ? 'SINGLE_PROCESSOR' : 'SMART';
    this.http.post(`${this.apiUrl}/loadtest/mode?mode=${newMode}`, {},
      { responseType: 'text' }
    ).subscribe(() => {
      this.currentMode = newMode;
    });
  }
}