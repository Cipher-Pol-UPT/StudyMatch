import type { IconComponent } from '../studymatch-ui';

export interface NavigationSection {
  key: string;
  path: string;
  icon: IconComponent;
  issues: number[];
}
