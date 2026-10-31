import { ApplicationInitStatus } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateLoader, TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';

import { StateStorageService } from 'app/core/auth';
import { provideTranslation } from './translation.provider';

describe('Language initialization', () => {
  async function initialize(
    locale: string | null,
  ): Promise<{ translate: TranslateService; storage: { getLocale: ReturnType<typeof vi.fn>; storeLocale: ReturnType<typeof vi.fn> } }> {
    const storage = { getLocale: vi.fn(() => locale), storeLocale: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        ...provideTranslation(),
        { provide: StateStorageService, useValue: storage },
        { provide: TranslateLoader, useValue: { getTranslation: () => of({}) } },
      ],
    });
    const initialization = TestBed.inject(ApplicationInitStatus);
    await initialization.donePromise;
    return { translate: TestBed.inject(TranslateService), storage };
  }

  it('starts in Spanish and saves that choice for reloads', async () => {
    const { translate, storage } = await initialize(null);
    expect(translate.getCurrentLang()).toBe('es');
    expect(storage.storeLocale).toHaveBeenCalledWith('es');
  });

  it('preserves a previous explicit choice of English', async () => {
    const { translate } = await initialize('en');
    expect(translate.getCurrentLang()).toBe('en');
  });

  it('recovers an unsupported stored language using Spanish', async () => {
    const { translate } = await initialize('fr');
    expect(translate.getCurrentLang()).toBe('es');
  });
});
