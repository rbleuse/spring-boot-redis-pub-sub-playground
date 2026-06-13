import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: class string from frontend-react/src/components/ui/label.tsx
@Directive({
  selector: 'label[appLabel]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"label"' },
})
export class LabelDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('flex items-center gap-2 text-sm leading-none font-medium select-none', this.userClass()),
  );
}
