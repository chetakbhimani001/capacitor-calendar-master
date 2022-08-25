import { Plugins } from '@capacitor/core';
declare module '@capacitor/core' {
  interface PluginRegistry {
    CapacitorCalendar: CapacitorCalendar;
  }
}
import type { CapacitorCalendar } from './definitions';

export * from './definitions';

export declare const Calendar: CapacitorCalendar;






