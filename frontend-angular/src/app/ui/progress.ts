import { Component, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: from frontend-react/src/components/ui/progress.tsx (radix Progress → plain div).
@Component({
  selector: 'app-progress',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"progress"' },
  template: `<div
    data-slot="progress-indicator"
    class="size-full flex-1 bg-primary transition-all"
    [style.transform]="'translateX(-' + (100 - (value() || 0)) + '%)'"
  ></div>`,
})
export class Progress {
  readonly value = input<number>(0);
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('relative flex h-1 w-full items-center overflow-x-hidden rounded-full bg-muted', this.userClass()),
  );
}
