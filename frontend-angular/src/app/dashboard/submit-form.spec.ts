import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { SubmitForm } from './submit-form';

describe('SubmitForm', () => {
  function make() {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(SubmitForm);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('is invalid when name is empty', () => {
    const c = make();
    c.form.controls.name.setValue('');
    expect(c.form.controls.name.valid).toBe(false);
  });

  it('rejects durationMs below 1000 and above 120000', () => {
    const c = make();
    c.form.controls.durationMs.setValue(500);
    expect(c.form.controls.durationMs.valid).toBe(false);
    c.form.controls.durationMs.setValue(200000);
    expect(c.form.controls.durationMs.valid).toBe(false);
    c.form.controls.durationMs.setValue(10000);
    expect(c.form.controls.durationMs.valid).toBe(true);
  });

  it('rejects failureRate outside 0..1', () => {
    const c = make();
    c.form.controls.failureRate.setValue(1.5);
    expect(c.form.controls.failureRate.valid).toBe(false);
    c.form.controls.failureRate.setValue(0.5);
    expect(c.form.controls.failureRate.valid).toBe(true);
  });
});
