import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-root',
  standalone: false,
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent implements OnInit, OnDestroy {

  private apiUrl = 'http://localhost:8080/api';
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
  comparisonResult: any = null;
  loadTestRunning: boolean = false;
  systemStatus: string = 'HEALTHY';

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
        processor.totalHandled = parseInt(this.extract(line, 'totalHandled: ', '\n'));
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
      const latestPayment = {
        processor: response.split('on: ')[1]?.split(' |')[0] || '',
        status: response.includes('SUCCESS') ? 'SUCCESS' : 'FAILED',
        amount: '500'
      };
      this.loadLastRouting();
      this.loadStats();
      this.loadPayments();
      this.pollHealth();
    });
  }

  loadLastRouting() {
    this.http.get(`${this.apiUrl}/payments`).subscribe((data: any) => {
      if (data && data.length > 0) {
        const lastPayment = data[0];
        this.http.get(`${this.apiUrl}/payments/${lastPayment.id}/routing`).subscribe((routing: any) => {
          this.lastRouting = {
            payment: lastPayment,
            decisions: routing
          };
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
    });
  }

  runSmartTest() {
    this.loadTestRunning = true;
    this.http.get(`${this.apiUrl}/loadtest/smart?payments=${this.loadTestCount}`,
      { responseType: 'text' }
    ).subscribe(result => {
      this.parseTestResult(result, 'smart');
      this.loadTestRunning = false;
      this.loadStats();
      this.loadPayments();
      this.pollHealth();
    });
  }

  runSingleTest() {
    this.loadTestRunning = true;
    this.http.get(`${this.apiUrl}/loadtest/single?payments=${this.loadTestCount}`,
      { responseType: 'text' }
    ).subscribe(result => {
      this.parseTestResult(result, 'single');
      this.loadTestRunning = false;
    });
  }

  parseTestResult(result: string, type: string) {
    const parts = result.split('|').map(p => p.trim());
    const total = parseInt(parts[1].split(':')[1].trim());
    const success = parseInt(parts[2].split(':')[1].trim());
    const failed = parseInt(parts[3].split(':')[1].trim());
    const rate = parseFloat(parts[4].split(':')[1].trim());

    if (!this.comparisonResult) this.comparisonResult = {};
    this.comparisonResult[type] = { total, success, failed, rate };
  }

  flushRedis() {
    this.http.post(`${this.apiUrl}/redis/flush`, {}, { responseType: 'text' })
      .subscribe(() => {
        this.pollHealth();
      });
  }

  getImprovementText(): string {
    if (!this.comparisonResult?.smart || !this.comparisonResult?.single) return '';
    const diff = this.comparisonResult.smart.rate - this.comparisonResult.single.rate;
    return diff > 0 ? `+${diff.toFixed(1)}% better with smart routing` : `${diff.toFixed(1)}% vs single processor`;
  }
}
