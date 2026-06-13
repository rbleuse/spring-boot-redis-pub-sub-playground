import { Directive, computed, input } from '@angular/core';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ClassValue } from 'clsx';
import { cn } from '../lib/utils';

// ponytail: class strings copied from frontend-react/src/components/ui/badge.tsx
export const badgeVariants = cva(
  'group/badge inline-flex h-5 w-fit shrink-0 items-center justify-center gap-1 overflow-hidden rounded-4xl border border-transparent px-2 py-0.5 text-xs font-medium whitespace-nowrap transition-all focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 [&>svg]:pointer-events-none [&>svg]:size-3!',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground',
        secondary: 'bg-secondary text-secondary-foreground',
        destructive: 'bg-destructive/10 text-destructive focus-visible:ring-destructive/20',
        outline: 'border-border text-foreground',
        ghost: 'hover:bg-muted hover:text-muted-foreground',
        link: 'text-primary underline-offset-4 hover:underline',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);

type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>['variant']>;

@Directive({
  selector: '[appBadge]',
  standalone: true,
  host: { '[class]': 'computedClass()', '[attr.data-slot]': '"badge"', '[attr.data-variant]': 'variant()' },
})
export class BadgeDirective {
  readonly variant = input<BadgeVariant>('default');
  readonly userClass = input<ClassValue>('', { alias: 'class' });
  protected readonly computedClass = computed(() => cn(badgeVariants({ variant: this.variant() }), this.userClass()));
}
