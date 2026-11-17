import { Capacitor, registerPlugin } from '@capacitor/core';

export type SharedFile = { name: string; size: number; modified: number; mime: string };
export type ServerStatus = {
  running: boolean;
  starting: boolean;
  port: number;
  addresses: string[];
  directory: string;
  count: number;
  totalBytes: number;
  code: string;
  error: string | null;
};

interface LocalSharePlugin {
  getStatus(): Promise<ServerStatus>;
  startServer(): Promise<void>;
  stopServer(): Promise<void>;
  listFiles(): Promise<{ files: SharedFile[] }>;
  importFiles(): Promise<{ count: number }>;
  openFile(options: { name: string }): Promise<void>;
  shareFile(options: { name: string }): Promise<void>;
  deleteFile(options: { name: string }): Promise<void>;
  regenerateCode(): Promise<{ code: string }>;
  copyText(options: { text: string }): Promise<void>;
}

export const isAndroid = Capacitor.getPlatform() === 'android';
export const LocalShare = registerPlugin<LocalSharePlugin>('LocalShare');
