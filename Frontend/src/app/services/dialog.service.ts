import { Injectable, signal } from '@angular/core';

export type DialogMode = 'alert' | 'confirm' | 'input';

@Injectable({ providedIn: 'root' })
export class DialogService {
  visible = signal(false);
  mode = signal<DialogMode>('alert');
  title = signal('');
  message = signal('');
  confirmText = signal('Aceptar');
  cancelText = signal('Cancelar');
  danger = signal(false);
  inputValue = signal('');
  inputPlaceholder = signal('');

  private resolver: ((value: any) => void) | null = null;

  /** Reemplazo de window.alert(): muestra la pantalla propia de la app y espera a que la cierren. */
  alert(message: string, title: string = 'Aviso'): Promise<void> {
    this.mode.set('alert');
    this.title.set(title);
    this.message.set(message);
    this.confirmText.set('Entendido');
    this.danger.set(false);
    this.visible.set(true);

    return new Promise<void>((resolve) => {
      this.resolver = resolve;
    });
  }

  /** Reemplazo de window.confirm(): devuelve true/false según lo que elija el usuario. */
  confirm(message: string, title: string = 'Confirmar', options?: { confirmText?: string; cancelText?: string; danger?: boolean }): Promise<boolean> {
    this.mode.set('confirm');
    this.title.set(title);
    this.message.set(message);
    this.confirmText.set(options?.confirmText || 'Confirmar');
    this.cancelText.set(options?.cancelText || 'Cancelar');
    this.danger.set(!!options?.danger);
    this.visible.set(true);

    return new Promise<boolean>((resolve) => {
      this.resolver = resolve;
    });
  }

  /** Reemplazo de window.prompt(): muestra un input de texto y devuelve el valor escrito, o null si cancelan. */
  input(message: string, title: string = 'Ingresa un valor', placeholder: string = '', defaultValue: string = ''): Promise<string | null> {
    this.mode.set('input');
    this.title.set(title);
    this.message.set(message);
    this.confirmText.set('Aceptar');
    this.cancelText.set('Cancelar');
    this.danger.set(false);
    this.inputPlaceholder.set(placeholder);
    this.inputValue.set(defaultValue);
    this.visible.set(true);

    return new Promise<string | null>((resolve) => {
      this.resolver = resolve;
    });
  }

  respond(value: boolean | string | null) {
    this.visible.set(false);
    if (this.resolver) {
      this.resolver(value);
      this.resolver = null;
    }
  }
}