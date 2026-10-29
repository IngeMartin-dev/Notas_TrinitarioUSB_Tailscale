import { Component, inject } from '@angular/core';
import { ConnectionService } from '../services/connection.service';

/**
 * Pantalla "Sin conexión con el servidor" a pantalla completa (cubre la
 * sidebar y todo el contenido). Se muestra encima de la app mientras
 * ConnectionService.offline() sea true y desaparece sola al reconectar.
 */
@Component({
  selector: 'app-connection-lost',
  standalone: true,
  templateUrl: './connection-lost.html',
  styleUrls: ['../not-found/not-found.css', './connection-lost.css']
})
export class ConnectionLost {
  connection = inject(ConnectionService);

  retry() {
    this.connection.checkNow();
  }
}