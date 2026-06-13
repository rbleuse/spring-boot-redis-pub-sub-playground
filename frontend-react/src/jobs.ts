import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';

// ponytail: one module for types + API + live store; split when it hurts.

export type JobStatus = 'SCHEDULED' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';

export interface Job {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  submittedAt: string;
  updatedAt: string;
  scheduledAt?: string;
  workerId?: string;
  error?: string;
}

export interface JobProgressEvent {
  jobId: string;
  name: string;
  status: JobStatus;
  progress: number;
  scheduledAt?: string;
  workerId?: string;
  error?: string;
  timestamp: string;
}

export interface SubmitJobRequest {
  name: string;
  durationMs?: number;
  failureRate?: number;
  scheduledAt?: string;
}

export function isTerminal(status: JobStatus): boolean {
  return status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
}

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';
const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws';

export async function submitJob(req: SubmitJobRequest): Promise<{ jobId: string }> {
  const res = await fetch(`${API_BASE}/jobs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(req),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => undefined);
    throw new Error(body?.detail ?? `Submit failed (${res.status})`);
  }
  return res.json();
}

export async function cancelJob(jobId: string): Promise<void> {
  const res = await fetch(`${API_BASE}/jobs/${jobId}/cancel`, { method: 'POST' });
  if (!res.ok) {
    const body = await res.json().catch(() => undefined);
    throw new Error(body?.detail ?? `Cancel failed (${res.status})`);
  }
}

/** Merge a live event into a job. When `prior` is undefined, create one (event seen before REST seed). */
function mergeEvent(prior: Job | undefined, event: JobProgressEvent): Job {
  return {
    jobId: event.jobId,
    name: event.name,
    status: event.status,
    progress: event.progress,
    scheduledAt: event.scheduledAt,
    workerId: event.workerId,
    error: event.error,
    submittedAt: prior?.submittedAt ?? event.timestamp,
    updatedAt: event.timestamp,
  };
}

export type ConnectionStatus = 'closed' | 'reconnecting' | 'connected';

/** REST-seed the job list, then keep it live via the STOMP firehose. */
export function useJobs(): { jobs: Job[]; status: ConnectionStatus } {
  const [byId, setById] = useState<Map<string, Job>>(new Map());
  // starts 'reconnecting': the effect connects on mount
  const [status, setStatus] = useState<ConnectionStatus>('reconnecting');

  useEffect(() => {
    fetch(`${API_BASE}/jobs`)
      .then((res) => res.json())
      .then((jobs: Job[]) =>
        setById((prev) => {
          const next = new Map(prev);
          for (const job of jobs) {
            const prior = next.get(job.jobId);
            // keep whichever representation has the latest update
            next.set(job.jobId, prior && prior.updatedAt > job.updatedAt ? prior : job);
          }
          return next;
        }),
      )
      .catch(() => {}); // live stream still populates on reconnect

    const client = new Client({ brokerURL: WS_URL, reconnectDelay: 5000 });
    client.onConnect = () => {
      setStatus('connected');
      client.subscribe('/topic/jobs', (msg) => {
        const event = JSON.parse(msg.body) as JobProgressEvent;
        setById((prev) => new Map(prev).set(event.jobId, mergeEvent(prev.get(event.jobId), event)));
      });
    };
    client.onWebSocketClose = () => setStatus(client.active ? 'reconnecting' : 'closed');
    client.activate();
    return () => void client.deactivate();
  }, []);

  const jobs = [...byId.values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  return { jobs, status };
}
