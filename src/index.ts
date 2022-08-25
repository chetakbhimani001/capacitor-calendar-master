import { Plugins } from '@capacitor/core';
declare module '@capacitor/core' {
  interface PluginRegistry {
    CapacitorCalendar: CapacitorCalendarPlugin;
  }
}
import type { CapacitorCalendarPlugin } from './definitions';

export * from './definitions';

export declare const Calendar: CapacitorCalendarPlugin;






