import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'ru.localshare.mobile',
  appName: 'LocalShare',
  webDir: 'dist',
  android: { allowMixedContent: false },
};
export default config;
