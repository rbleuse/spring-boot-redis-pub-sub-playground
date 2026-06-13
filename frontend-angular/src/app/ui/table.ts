import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: from frontend-react/src/components/ui/table.tsx — class strings verbatim.
// The container's overflow wrapper is plain markup in the consuming template.

@Directive({ selector: 'table[appTable]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('w-full caption-bottom text-sm', this.userClass()));
}

@Directive({ selector: 'thead[appTableHeader]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableHeaderDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('[&_tr]:border-b', this.userClass()));
}

@Directive({ selector: 'tbody[appTableBody]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableBodyDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('[&_tr:last-child]:border-0', this.userClass()));
}

@Directive({ selector: 'tr[appTableRow]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableRowDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted', this.userClass()),
  );
}

@Directive({ selector: 'th[appTableHead]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableHeadDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('h-10 px-2 text-left align-middle font-medium whitespace-nowrap text-foreground', this.userClass()),
  );
}

@Directive({ selector: 'td[appTableCell]', standalone: true, host: { '[class]': 'computedClass()' } })
export class TableCellDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('p-2 align-middle whitespace-nowrap', this.userClass()));
}
