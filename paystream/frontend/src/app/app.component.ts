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
  forceOpen(processorName: string) {
      this.http.post(`${this.apiUrl}/simulate/forceopen/${processorName}`, {},
          { responseType: 'text' }
      ).subscribe(() => {
          this.pollHealth();
          this.loadEvents();
          this.loadStats();
          this.loadPayments();
      });
  }
  runLoadTest() {
    const payments = Number(this.loadTestCount);
    const threads = Number(this.threadCount);

    if (threads < 1 || threads > 20) {
        this.loadTestResult = 'Payments must be between 1 and 200 & Threads must be between 1 and 20';
        return;
    }

    if (payments < 1 || payments > 200) {
        this.loadTestResult = 'Payments must be between 1 and 200';
        return;
    }
    if (threads < 1 || threads > 20) {
        this.loadTestResult = 'Threads must be between 1 and 20';
        return;
    }

    this.loadTestRunning = true;
    this.loadTestResult = '';
    
    clearInterval(this.pollInterval);
    this.pollInterval = setInterval(() => {
        this.pollHealth();
        this.loadStats();
        this.loadPayments();
        this.loadEvents();
    }, 1000);

    this.http.get(`${this.apiUrl}/loadtest/smart?payments=${payments}&threads=${threads}`,
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

    confirmForceOpen(processorName: string) {
      const dialogRef = document.createElement('div');
      dialogRef.innerHTML = `
          <div style="
              position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
              background: rgba(0,0,0,0.3); display: flex; align-items: center;
              justify-content: center; z-index: 1000;">
              <div style="
                  background: white; border-radius: 10px; padding: 24px 28px;
                  box-shadow: 0 8px 32px rgba(0,0,0,0.15); max-width: 360px; width: 90%;">
                  <div style="font-size: 14px; font-weight: 600; color: #1a1a1a; margin-bottom: 8px;">
                      Force Open Circuit
                  </div>
                  <div style="font-size: 13px; color: #666; margin-bottom: 20px; line-height: 1.5;">
                      This will immediately open the <strong>${processorName}</strong> circuit breaker,
                      blocking all traffic until recovery. Continue?
                  </div>
                  <div style="display: flex; gap: 10px; justify-content: flex-end;">
                      <button id="cancel-btn" style="
                          padding: 7px 16px; background: white; color: #666;
                          border: 1px solid #ddd; border-radius: 6px; font-size: 13px;
                          cursor: pointer;">
                          Cancel
                      </button>
                      <button id="confirm-btn" style="
                          padding: 7px 16px; background: #c62828; color: white;
                          border: none; border-radius: 6px; font-size: 13px;
                          cursor: pointer; font-weight: 500;">
                          Force OPEN
                      </button>
                  </div>
              </div>
          </div>
      `;
      document.body.appendChild(dialogRef);

      document.getElementById('cancel-btn')!.onclick = () => {
          document.body.removeChild(dialogRef);
      };
      document.getElementById('confirm-btn')!.onclick = () => {
          document.body.removeChild(dialogRef);
          this.forceOpen(processorName);
      };
  }
  
}