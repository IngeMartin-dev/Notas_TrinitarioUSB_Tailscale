import { Component, Input, Output, EventEmitter, inject, OnInit } from '@angular/core';

import { MessageService, Message } from './services/message.service';
import { NotificationService, Notification } from './services/notification.service';

export interface MessageClickEvent {
  item: Message | Notification;
  type: 'message' | 'notification';
  action: 'view' | 'reply';
}

interface DropdownItem {
  key: string;
  id: number;
  kind: 'message' | 'notification';
  raw: Message | Notification;
  title: string;
  text: string;
  sender: string;
  picture: string;
  icon: string;
  color: string;
  colorSoft: string;
  label: string;
  unread: boolean;
  ts: number;
  time: string;
}

@Component({
  selector: 'app-messages-dropdown',
  standalone: true,
  imports: [],
  template: `
    @if (isOpen) {
      <div class="messages-dropdown" [class.dropdown-open]="isOpen">
        <!-- Cabecera -->
        <div class="dd-header">
          <div class="dd-title">
            <span class="dd-title-icon material-icons">notifications_active</span>
            <div>
              <h3>Bandeja</h3>
              <small>{{ unreadCount() > 0 ? unreadCount() + ' sin leer' : 'Todo al día' }}</small>
            </div>
          </div>
          <button class="close-btn" aria-label="Cerrar" (click)="closeDropdown()">
            <span class="material-icons">close</span>
          </button>
        </div>

        <!-- Pestañas -->
        <div class="dd-tabs" role="tablist">
          <button role="tab" class="dd-tab" [class.active]="tab === 'all'" (click)="tab = 'all'">
            Todo <span class="dd-count">{{ totalCount() }}</span>
          </button>
          <button role="tab" class="dd-tab" [class.active]="tab === 'unread'" (click)="tab = 'unread'">
            Sin leer <span class="dd-count" [class.hot]="unreadCount() > 0">{{ unreadCount() }}</span>
          </button>
          <button role="tab" class="dd-tab" [class.active]="tab === 'replies'" (click)="tab = 'replies'">
            Respuestas <span class="dd-count">{{ filteredMessages.length }}</span>
          </button>
        </div>

        @if (visibleItems().length > 0) {
          <div class="combined-list">
            @for (group of groupedItems(); track group.label) {
              <div class="dd-group-label">{{ group.label }}</div>
              @for (it of group.items; track it.key) {
                <div
                  class="dd-item"
                  [class.unread]="it.unread"
                  [class.deleting]="deletingItems.has(it.id)"
                  (click)="markAsReadAndClose(it.raw, it.kind)">
                  <div class="dd-avatar" [style.background]="it.color">
                    @if (it.picture) {
                      <img [src]="it.picture" [alt]="it.sender" />
                    } @else {
                      <span class="material-icons">{{ it.icon }}</span>
                    }
                  </div>
                  <div class="dd-body">
                    <div class="dd-row">
                      <span class="dd-item-title">{{ it.title }}</span>
                      <span class="dd-time">{{ it.time }}</span>
                    </div>
                    <div class="dd-sender">
                      <span class="dd-chip" [style.color]="it.color" [style.background]="it.colorSoft">{{ it.label }}</span>
                      <span class="dd-from">{{ it.sender }}</span>
                    </div>
                    <div class="dd-text">{{ it.text }}</div>
                  </div>
                  @if (it.unread) {
                    <span class="dd-dot" aria-label="Sin leer"></span>
                  }
                </div>
              }
            }
          </div>
        } @else {
          <div class="empty-state">
            <span class="material-icons">{{ tab === 'unread' ? 'done_all' : 'mark_email_read' }}</span>
            <p>{{ tab === 'unread' ? '¡Estás al día!' : 'Aquí aparecerán tus avisos' }}</p>
            <small>{{ tab === 'unread' ? 'No tienes nada pendiente por leer.' : 'Las notificaciones y respuestas llegarán a esta bandeja.' }}</small>
          </div>
        }

        @if (hasContent()) {
          <div class="dropdown-footer">
            <button class="mark-all-read-btn" [disabled]="!hasUnreadItems()" (click)="markAllAsRead()">
              <span class="material-icons">done_all</span> Marcar todo como leído
            </button>
            <button class="delete-notifications-btn" (click)="deleteAllNotifications()">
              <span class="material-icons">delete_sweep</span> Limpiar
            </button>
          </div>
        }
      </div>
    }
    `,
  styles: [`
    .messages-dropdown {
      position: absolute;
      top: calc(100% + 10px);
      right: 0;
      width: 420px;
      min-height: 220px;
      max-height: min(600px, 78vh);
      display: flex;
      flex-direction: column;
      background: var(--surface);
      border-radius: 18px;
      box-shadow: 0 24px 60px rgba(15, 23, 42, 0.22), 0 4px 14px rgba(15, 23, 42, 0.08);
      border: 1px solid var(--border);
      z-index: 1000;
      animation: dropdownSlide 0.22s cubic-bezier(.2,.8,.2,1);
      overflow: hidden;
    }
    @keyframes dropdownSlide {
      from { opacity: 0; transform: translateY(-8px) scale(.98); }
      to   { opacity: 1; transform: translateY(0) scale(1); }
    }

    /* Cabecera */
    .dd-header {
      padding: 18px 20px 14px;
      display: flex; justify-content: space-between; align-items: center;
      background: linear-gradient(135deg, var(--brand) 0%, #1d4ed8 100%);
      color: #fff;
    }
    .dd-title { display: flex; align-items: center; gap: 12px; }
    .dd-title-icon {
      width: 40px; height: 40px; border-radius: 12px;
      background: rgba(255,255,255,.18);
      display: flex; align-items: center; justify-content: center; font-size: 22px;
    }
    .dd-title h3 { margin: 0; font-size: 17px; font-weight: 700; letter-spacing: .2px; }
    .dd-title small { opacity: .85; font-size: 12px; }
    .close-btn {
      background: rgba(255,255,255,.15); border: none; width: 32px; height: 32px;
      border-radius: 50%; cursor: pointer; color: #fff; display: flex;
      align-items: center; justify-content: center; transition: background .18s ease;
    }
    .close-btn:hover { background: rgba(255,255,255,.3); }
    .close-btn .material-icons { font-size: 18px; }
    .close-btn:focus-visible { outline: 2px solid #fff; outline-offset: 2px; }

    /* Pestañas */
    .dd-tabs { display: flex; gap: 6px; padding: 10px 14px; background: var(--surface-2); border-bottom: 1px solid var(--border); }
    .dd-tab {
      flex: 1; border: none; background: transparent; padding: 8px 6px; border-radius: 10px;
      font-size: 12.5px; font-weight: 600; color: var(--text-3); cursor: pointer;
      display: flex; align-items: center; justify-content: center; gap: 6px; transition: all .18s ease;
    }
    .dd-tab:hover { background: var(--surface); color: var(--text-1); }
    .dd-tab.active { background: var(--surface); color: var(--brand); box-shadow: 0 1px 4px rgba(15,23,42,.1); }
    .dd-count { background: var(--border); color: var(--text-2); font-size: 11px; padding: 1px 7px; border-radius: 999px; }
    .dd-count.hot { background: #ef4444; color: #fff; }

    /* Lista */
    .combined-list { flex: 1; overflow-y: auto; padding: 4px 0 8px; }
    .dd-group-label {
      padding: 12px 20px 6px; font-size: 11px; font-weight: 700; letter-spacing: .8px;
      text-transform: uppercase; color: var(--text-4);
    }
    .dd-item {
      position: relative; display: flex; gap: 12px; align-items: flex-start;
      padding: 12px 20px; cursor: pointer; transition: background .15s ease, opacity .3s ease, transform .3s ease;
    }
    .dd-item:hover { background: var(--surface-2); }
    .dd-item.unread { background: color-mix(in srgb, var(--brand) 6%, transparent); }
    .dd-item.unread:hover { background: color-mix(in srgb, var(--brand) 12%, transparent); }
    .dd-item.deleting { opacity: 0; transform: translateX(40px); }

    .dd-avatar {
      width: 42px; height: 42px; border-radius: 14px; flex-shrink: 0; overflow: hidden;
      display: flex; align-items: center; justify-content: center; color: #fff;
      box-shadow: 0 3px 8px rgba(15,23,42,.15);
    }
    .dd-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .dd-avatar .material-icons { font-size: 22px; }

    .dd-body { flex: 1; min-width: 0; }
    .dd-row { display: flex; justify-content: space-between; gap: 8px; align-items: baseline; }
    .dd-item-title {
      font-size: 14px; font-weight: 600; color: var(--text-1);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    .dd-item.unread .dd-item-title { font-weight: 700; }
    .dd-time { font-size: 11px; color: var(--text-4); white-space: nowrap; }
    .dd-sender { display: flex; align-items: center; gap: 8px; margin: 3px 0 4px; min-width: 0; }
    .dd-chip { font-size: 10.5px; font-weight: 700; padding: 2px 8px; border-radius: 999px; white-space: nowrap; }
    .dd-from { font-size: 12px; color: var(--text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .dd-text {
      font-size: 13px; color: var(--text-2); line-height: 1.45; word-break: break-word;
      display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .dd-dot {
      position: absolute; top: 16px; right: 8px; width: 8px; height: 8px; border-radius: 50%;
      background: var(--brand); box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand) 22%, transparent);
    }

    /* Vacío */
    .empty-state { padding: 46px 24px; text-align: center; color: var(--text-3); }
    .empty-state .material-icons { font-size: 56px; margin-bottom: 10px; color: var(--brand); opacity: .35; }
    .empty-state p { margin: 0 0 4px; font-size: 15px; font-weight: 600; color: var(--text-2); }
    .empty-state small { font-size: 12.5px; }

    /* Pie */
    .dropdown-footer { padding: 10px 14px; border-top: 1px solid var(--border); background: var(--surface-2); display: flex; gap: 8px; }
    .mark-all-read-btn, .delete-notifications-btn {
      display: flex; align-items: center; justify-content: center; gap: 6px;
      padding: 9px 12px; border: none; border-radius: 10px; font-size: 12.5px; font-weight: 600; cursor: pointer;
      transition: all .18s ease;
    }
    .mark-all-read-btn { flex: 1; background: var(--brand); color: #fff; }
    .mark-all-read-btn:hover:not(:disabled) { filter: brightness(1.08); }
    .mark-all-read-btn:disabled { background: var(--border-strong); color: var(--text-3); cursor: not-allowed; opacity: .7; }
    .delete-notifications-btn { background: transparent; color: var(--text-2); border: 1px solid var(--border-strong); }
    .delete-notifications-btn:hover { background: #fee2e2; color: #b91c1c; border-color: #fecaca; }
    .mark-all-read-btn .material-icons, .delete-notifications-btn .material-icons { font-size: 17px; }

    .combined-list::-webkit-scrollbar { width: 6px; }
    .combined-list::-webkit-scrollbar-track { background: transparent; }
    .combined-list::-webkit-scrollbar-thumb { background: var(--border-strong); border-radius: 3px; }


    /* ----- Modo oscuro ----- */
    :host-context([data-theme="dark"]) .messages-dropdown {
      box-shadow: 0 24px 60px rgba(0, 0, 0, .7), 0 0 30px rgba(6, 182, 212, .12);
      border-color: var(--border-strong);
    }
    :host-context([data-theme="dark"]) .dd-header {
      background: linear-gradient(135deg, #0f172a 0%, #155e75 100%);
      border-bottom: 1px solid rgba(6, 182, 212, .25);
    }
    :host-context([data-theme="dark"]) .dd-title-icon { background: rgba(6, 182, 212, .2); color: var(--brand-500); }
    :host-context([data-theme="dark"]) .dd-tab.active { color: var(--brand-500); box-shadow: 0 0 0 1px var(--border-strong); }
    :host-context([data-theme="dark"]) .dd-item.unread { background: color-mix(in srgb, var(--brand) 10%, transparent); }
    :host-context([data-theme="dark"]) .dd-item.unread:hover { background: color-mix(in srgb, var(--brand) 16%, transparent); }
    /* Chips y avatares con colores más claros para que contrasten sobre fondo oscuro */
    :host-context([data-theme="dark"]) .dd-chip { filter: brightness(1.45) saturate(.9); }
    :host-context([data-theme="dark"]) .dd-avatar { box-shadow: 0 0 14px rgba(0, 0, 0, .5); }
    :host-context([data-theme="dark"]) .dd-dot { background: var(--brand-500); box-shadow: 0 0 0 3px rgba(6, 182, 212, .25), 0 0 10px rgba(6, 182, 212, .6); }
    :host-context([data-theme="dark"]) .mark-all-read-btn { background: var(--brand); color: #04141a; }
    :host-context([data-theme="dark"]) .mark-all-read-btn:disabled { background: var(--surface-2); color: var(--text-4); }
    :host-context([data-theme="dark"]) .delete-notifications-btn:hover { background: var(--danger-bg); color: #fca5a5; border-color: rgba(239, 68, 68, .4); }
    :host-context([data-theme="dark"]) .empty-state .material-icons { color: var(--brand-500); opacity: .5; }

    @media (max-width: 480px) {
      .messages-dropdown { width: calc(100vw - 24px); max-width: none; right: 12px; position: fixed; top: 64px; }
    }
  `]
})
export class MessagesDropdownComponent implements OnInit {
  @Input() isOpen = false;
  @Input() notifications: Notification[] = [];
  @Input() messages: Message[] = [];
  @Output() close = new EventEmitter<void>();
  @Output() messageClick = new EventEmitter<MessageClickEvent>();

  tab: 'all' | 'unread' | 'replies' = 'all';
  deletingItems = new Set<number>();
  deletedItems = new Set<number>();

  private messageService = inject(MessageService);
  private notificationService = inject(NotificationService);
  private readonly DELETED_STORAGE_KEY = 'deleted_notifications_messages';

  get filteredNotifications() {
    return this.notifications.filter(n => !this.deletedItems.has(n.id));
  }

  get filteredMessages() {
    return this.messages.filter(m => !this.deletedItems.has(m.id));
  }

  ngOnInit() {
    // Subscribe to messages changes if not provided via input
    this.messageService.getMessages().subscribe(messages => {
      if (this.messages.length === 0) {
        this.messages = messages;
      }
    });
    this.loadDeletedItems();
  }

  private loadDeletedItems(): void {
    try {
      const stored = localStorage.getItem(this.DELETED_STORAGE_KEY);
      if (stored) {
        const deletedIds = JSON.parse(stored);
        this.deletedItems = new Set(deletedIds);
      }
    } catch (error) {
      console.error('Error loading deleted items:', error);
      this.deletedItems.clear();
    }
  }

  private saveDeletedItems(): void {
    try {
      const deletedIds = Array.from(this.deletedItems);
      localStorage.setItem(this.DELETED_STORAGE_KEY, JSON.stringify(deletedIds));
    } catch (error) {
      console.error('Error saving deleted items:', error);
    }
  }

  closeDropdown() {
    this.isOpen = false;
    this.close.emit();
  }

  markAsReadAndClose(item: Message | Notification, type: 'message' | 'notification') {
    if (type === 'message' && !item.isRead) {
      this.messageService.markAsRead(item.id).subscribe();
    } else if (type === 'notification' && !item.isRead) {
      this.notificationService.markAsRead(item.id).subscribe();
    }
    this.messageClick.emit({
      item,
      type,
      action: 'view'
    });
    this.closeDropdown();
  }

  markAllAsRead() {
    const unreadMessages = this.messages.filter(m => !m.isRead);
    const unreadNotifications = this.notifications.filter(n => !n.isRead);

    unreadMessages.forEach(message => {
      this.messageService.markAsRead(message.id).subscribe();
    });

    unreadNotifications.forEach(notification => {
      this.notificationService.markAsRead(notification.id).subscribe();
    });
  }

  deleteAllNotifications() {
    // Animate removal one by one
    const allItems = [...this.filteredNotifications, ...this.filteredMessages];
    let index = 0;

    const removeNext = () => {
      if (index < allItems.length) {
        const item = allItems[index];
        // Add deleting class for animation
        this.deletingItems.add(item.id);
        // Mark as read
        if (this.notifications.find(n => n.id === item.id)) {
          this.notificationService.markAsRead(item.id).subscribe();
        } else {
          this.messageService.markAsRead(item.id).subscribe();
        }
        // Mark as deleted after animation
        setTimeout(() => {
          this.deletedItems.add(item.id);
          this.deletingItems.delete(item.id);
          this.saveDeletedItems();
        }, 500); // Match animation duration
        index++;
        setTimeout(removeNext, 200); // Delay for next item
      }
    };

    removeNext();
  }


  // ───────── Vista unificada (notificaciones + respuestas) ─────────
  private typeStyle(type: string): { icon: string; color: string; label: string } {
    switch (type) {
      case 'ADMIN_MESSAGE':       return { icon: 'campaign',        color: '#2563eb', label: 'Administración' };
      case 'REPORT_CARD_SENT':    return { icon: 'assignment',      color: '#f59e0b', label: 'Boletín' };
      case 'REPORT_CARD_SIGNED':  return { icon: 'verified',        color: '#16a34a', label: 'Firmado' };
      default:                    return { icon: 'notifications',   color: '#6366f1', label: 'Aviso' };
    }
  }

  private soft(hex: string): string {
    // Fondo suave del chip a partir del color principal.
    return `color-mix(in srgb, ${hex} 14%, transparent)`;
  }

  private toTime(iso: string): number {
    const t = new Date(iso).getTime();
    return isNaN(t) ? 0 : t;
  }

  private buildItems(): DropdownItem[] {
    const items: DropdownItem[] = [];

    for (const n of this.filteredNotifications) {
      const st = this.typeStyle(n.notificationType);
      const sender = n.user ? `${n.user.name || ''} ${n.user.surname || ''}`.trim() : '';
      items.push({
        key: 'n' + n.id, id: n.id, kind: 'notification', raw: n,
        title: n.title, text: n.message, sender: sender || 'Sistema',
        picture: n.user?.profilePicture || '', icon: st.icon, color: st.color, colorSoft: this.soft(st.color),
        label: st.label, unread: this.isNotificationNewlyArrived(n) || !n.isRead,
        ts: this.toTime(n.createdAt), time: this.formatNotificationTime(n.createdAt)
      });
    }

    for (const m of this.filteredMessages) {
      const color = '#0ea5e9';
      items.push({
        key: 'm' + m.id, id: m.id, kind: 'message', raw: m,
        title: `Respuesta a "${m.originalNotificationTitle}"`, text: m.replyMessage,
        sender: `${m.senderName || ''} ${m.senderSurname || ''}`.trim(),
        picture: '', icon: 'reply', color, colorSoft: this.soft(color),
        label: 'Respuesta', unread: !m.isRead,
        ts: this.toTime(m.createdAt), time: this.formatMessageTime(m.createdAt)
      });
    }

    return items.sort((a, b) => b.ts - a.ts);
  }

  visibleItems(): DropdownItem[] {
    const all = this.buildItems();
    if (this.tab === 'unread') return all.filter(i => i.unread);
    if (this.tab === 'replies') return all.filter(i => i.kind === 'message');
    return all;
  }

  groupedItems(): { label: string; items: DropdownItem[] }[] {
    const start = new Date(); start.setHours(0, 0, 0, 0);
    const today = start.getTime();
    const yesterday = today - 86400000;
    const groups: { label: string; items: DropdownItem[] }[] = [
      { label: 'Hoy', items: [] }, { label: 'Ayer', items: [] }, { label: 'Anteriores', items: [] }
    ];
    for (const it of this.visibleItems()) {
      if (it.ts >= today) groups[0].items.push(it);
      else if (it.ts >= yesterday) groups[1].items.push(it);
      else groups[2].items.push(it);
    }
    return groups.filter(g => g.items.length > 0);
  }

  unreadCount(): number {
    return this.buildItems().filter(i => i.unread).length;
  }

  totalCount(): number {
    return this.filteredNotifications.length + this.filteredMessages.length;
  }

  hasContent(): boolean {
    return this.filteredNotifications.length > 0 || this.filteredMessages.length > 0;
  }

  hasUnreadItems(): boolean {
    return this.filteredNotifications.some(n => !n.isRead) || this.filteredMessages.some(m => !m.isRead);
  }

  formatMessageTime(createdAt: string): string {
    return this.messageService.formatTimeAgo(createdAt);
  }

  formatNotificationTime(createdAt: string): string {
    return this.notificationService.formatTimeAgo(createdAt);
  }

  isNotificationNewlyArrived(notification: Notification): boolean {
    return this.notificationService.isNewlyArrived(notification);
  }

  trackByMessageId(index: number, message: Message): number {
    return message.id;
  }

  trackByNotificationId(index: number, notification: Notification): number {
    return notification.id;
  }
}
