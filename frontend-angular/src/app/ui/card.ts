import { Directive, computed, input } from '@angular/core';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: card primitives from frontend-react/src/components/ui/card.tsx — only the
// slots actually used (card, header, title, content). Add footer/description when needed.

@Directive({
  selector: '[appCard]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"card"' },
})
export class CardDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn(
      'group/card flex flex-col gap-(--card-spacing) overflow-hidden rounded-xl bg-card py-(--card-spacing) text-sm text-card-foreground ring-1 ring-foreground/10 [--card-spacing:--spacing(4)]',
      this.userClass(),
    ),
  );
}

@Directive({
  selector: '[appCardHeader]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"card-header"' },
})
export class CardHeaderDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('grid auto-rows-min items-start gap-1 px-(--card-spacing)', this.userClass()),
  );
}

@Directive({
  selector: '[appCardTitle]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"card-title"' },
})
export class CardTitleDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() =>
    cn('font-heading text-base leading-snug font-medium', this.userClass()),
  );
}

@Directive({
  selector: '[appCardContent]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"card-content"' },
})
export class CardContentDirective {
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn('px-(--card-spacing)', this.userClass()));
}
