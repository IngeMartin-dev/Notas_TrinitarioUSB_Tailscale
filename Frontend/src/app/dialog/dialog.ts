import { Component, inject } from '@angular/core';
import { DialogService } from '../services/dialog.service';

@Component({
  selector: 'app-dialog',
  standalone: true,
  templateUrl: './dialog.html',
  styleUrl: './dialog.css'
})
export class Dialog {
  dialogService = inject(DialogService);

  respond(value: boolean | string | null) {
    this.dialogService.respond(value);
  }

  onInput(value: string) {
    this.dialogService.inputValue.set(value);
  }
}
