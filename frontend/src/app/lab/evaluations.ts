import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';
import { Evaluation } from '../api/evaluation';
@Injectable({providedIn:'root'})
export class Evaluations {
  private readonly http = inject(HttpClient);
  private readonly lists = new Map<string,Observable<string[]>>();
  private readonly reports = new Map<string,Observable<Evaluation>>();
  timestamps(version: string, split: string): Observable<string[]> {
    const path = this.path(version,split);
    if (!this.lists.has(path)) this.lists.set(path,this.http.get<string[]>(path).pipe(shareReplay({bufferSize:1,refCount:false})));
    return this.lists.get(path)!;
  }
  report(version: string, split: string, timestamp: string): Observable<Evaluation> {
    const path = this.path(version,split) + '/' + encodeURIComponent(timestamp);
    if (!this.reports.has(path)) this.reports.set(path,this.http.get<Evaluation>(path).pipe(shareReplay({bufferSize:1,refCount:false})));
    return this.reports.get(path)!;
  }
  private path(version: string, split: string): string { return '/api/evaluations/' + encodeURIComponent(version) + '/' + encodeURIComponent(split); }
}


