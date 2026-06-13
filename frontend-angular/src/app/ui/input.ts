import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: class string from frontend-react/src/components/ui/input.tsx
@Directive({
  selector: 'input[appInput]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"input"' },
})
export class InputDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn(
      'h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:bg-input/50 disabled:opacity-50 aria-invalid:border-destructive aria-invalid:ring-3 aria-invalid:ring-destructive/20 md:text-sm',
      this.userClass(),
    ),
  );
}
