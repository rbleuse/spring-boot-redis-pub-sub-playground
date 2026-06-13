import { useState, type FormEvent } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { cancelJob, isTerminal, submitJob, useJobs, type JobStatus } from './jobs';

const statusBadge: Record<JobStatus, { variant: 'default' | 'secondary' | 'destructive' | 'outline'; className?: string }> = {
  SCHEDULED: { variant: 'outline', className: 'text-purple-600 border-purple-300' },
  QUEUED: { variant: 'secondary' },
  RUNNING: { variant: 'default', className: 'bg-blue-600 text-white' },
  COMPLETED: { variant: 'default', className: 'bg-green-600 text-white' },
  FAILED: { variant: 'destructive' },
  CANCELLED: { variant: 'outline', className: 'text-muted-foreground' },
};

const connBadge: Record<string, string> = {
  connected: 'bg-green-600 text-white',
  reconnecting: 'bg-amber-500 text-white',
  closed: '',
};

export default function App() {
  const { jobs, status } = useJobs();
  const [notice, setNotice] = useState('');

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const scheduledAt = data.get('scheduledAt') as string;
    try {
      const { jobId } = await submitJob({
        name: data.get('name') as string,
        durationMs: Number(data.get('durationMs')),
        failureRate: Number(data.get('failureRate')),
        scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : undefined,
      });
      setNotice(`Submitted ${jobId}`);
      form.reset();
    } catch (err) {
      setNotice(err instanceof Error ? err.message : 'Submit failed');
    }
  }

  return (
    <main className="mx-auto max-w-5xl space-y-6 p-6">
      <header className="flex items-baseline gap-3">
        <h1 className="text-2xl font-semibold">Job Monitor</h1>
        <Badge variant="secondary" className={`capitalize ${connBadge[status] ?? ''}`}>
          {status}
        </Badge>
      </header>

      <Card>
        <CardHeader>
          <CardTitle>Submit a job</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={onSubmit} className="flex flex-wrap items-end gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="name">Name</Label>
              <Input id="name" name="name" required maxLength={100} placeholder="my-job" />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="durationMs">Duration (ms)</Label>
              <Input
                id="durationMs"
                name="durationMs"
                type="number"
                min={1000}
                max={120000}
                defaultValue={10000}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="failureRate">Failure rate</Label>
              <Input
                id="failureRate"
                name="failureRate"
                type="number"
                min={0}
                max={1}
                step={0.05}
                defaultValue={0}
                required
              />
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="scheduledAt">Scheduled at</Label>
              <Input id="scheduledAt" name="scheduledAt" type="datetime-local" />
            </div>
            <Button type="submit">Submit</Button>
            {notice && <span className="text-sm text-muted-foreground">{notice}</span>}
          </form>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Jobs</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Job</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[220px]">Progress</TableHead>
                <TableHead>Scheduled at</TableHead>
                <TableHead>Worker</TableHead>
                <TableHead>Updated</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {jobs.map((job) => (
                <TableRow key={job.jobId}>
                  <TableCell>
                    <div className="font-medium">{job.name}</div>
                    <div className="text-xs text-muted-foreground">{job.jobId}</div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={statusBadge[job.status].variant} className={statusBadge[job.status].className}>
                      {job.status}
                    </Badge>
                    {job.error && <div className="mt-1 text-xs text-destructive">{job.error}</div>}
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Progress value={job.progress} className="w-36" />
                      <span className="text-xs tabular-nums">{job.progress}%</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    {job.scheduledAt ? new Date(job.scheduledAt).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell>{job.workerId ?? '—'}</TableCell>
                  <TableCell>{new Date(job.updatedAt).toLocaleTimeString()}</TableCell>
                  <TableCell>
                    {!isTerminal(job.status) && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => cancelJob(job.jobId).catch((e) => setNotice(e.message))}
                      >
                        Cancel
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {jobs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-muted-foreground">
                    No jobs yet — submit one above.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </main>
  );
}
