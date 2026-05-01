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
  sendingPayment: boolean = false;
  selectedPaymentId: number | null = null;

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
    document.addEventListener('click', (event: MouseEvent) => {
        const table = document.querySelector('.payment-table');
        if (table && !table.contains(event.target as Node)) {
            this.selectedPaymentId = null;
        }
    });
  }

  ngOnDestroy() {
    clearInterval(this.pollInterval);
  }

  pollHealth() {
    this.http.get<any[]>(`${this.apiUrl}/health`).subscribe(data => {
        data.forEach(h => {
            const processor = this.processors.find(p => p.name === h.name);
            if (processor) {
                processor.state = h.state;
                processor.consecutiveFailures = h.consecutiveFailures;
                processor.consecutiveSuccesses = h.consecutiveSuccesses;
                processor.avgLatency = h.avgLatency;
                processor.successRate = h.successRate;
                processor.score = h.score;
            }
        });
        this.updateSystemStatus();
    });
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
      this.events = data;
    });
  }

  sendPayment() {
    this.sendingPayment = true;
    this.http.post(`${this.apiUrl}/payment`,
        { amount: '500', currency: 'INR' },
        { responseType: 'text' }
    ).subscribe(response => {
        this.sendingPayment = false;
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
    
    clearInterval(this.pollInterval);
    this.pollInterval = setInterval(() => {
        this.pollHealth();
        this.loadStats();
        this.loadPayments();
        this.loadEvents();
    }, 1000);

    this.http.get(`${this.apiUrl}/loadtest/smart?payments=${this.loadTestCount}&threads=${this.threadCount}`,
        { responseType: 'text' }
    ).subscribe(result => {
        this.loadTestResult = result;
        this.loadTestRunning = false;
        
        clearInterval(this.pollInterval);
        this.pollInterval = setInterval(() => {
            this.pollHealth();
            this.loadStats();
            this.loadPayments();
            this.loadEvents();
        }, 5000);
        
        this.pollHealth();
        this.loadStats();
        this.loadPayments();
        this.loadEvents();
    });
  }

  flushRedis() {
    if (!confirm('Reset all processor scores? This will wipe all Redis data.')) return;
    this.http.post(`${this.apiUrl}/redis/flush`, {}, { responseType: 'text' })
        .subscribe(() => {
            this.pollHealth();
            this.loadStats();
        });
  }
  
  formatTime(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr + 'Z');
    return date.toLocaleTimeString('en-IN', { 
      hour: '2-digit', 
      minute: '2-digit', 
      second: '2-digit', 
      fractionalSecondDigits: 3,
      hour12: false 
    });
  }

  selectPayment(payment: any) {
    this.selectedPaymentId = payment.id;
    this.http.get(`${this.apiUrl}/payments/${payment.id}/routing`).subscribe((routing: any) => {
        this.lastRouting = { payment: payment, decisions: routing };
    });
  }
}