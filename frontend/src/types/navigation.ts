import type { IconComponent } from '../studymatch-ui';

export interface NavigationSection {
  key: string;
  path: string;
  icon: IconComponent;
  // has a real page; sections that aren't ready yet get a "coming soon" placeholder
  ready?: boolean;
  // i18n key for the home card, when it differs from workspace.descriptions.<key>
  description?: string;
}
