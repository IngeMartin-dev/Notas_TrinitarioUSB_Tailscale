import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { Chats } from './chats';
import { ChatService, ChatContactDto } from '../services/chat.service';
import { FirebasePushService } from '../services/firebase-push.service';
import { of } from 'rxjs';

describe('Chats', () => {
  let component: Chats;
  let fixture: ComponentFixture<Chats>;
  let chatServiceSpy: jasmine.SpyObj<ChatService>;

  const mockContacts: ChatContactDto[] = [
    {
      id: 1,
      name: 'Juan',
      surname: 'Pérez',
      profilePicture: null,
      role: 'TEACHER',
      lastMessage: 'Hola',
      lastMessageAt: new Date().toISOString(),
      unreadCount: 1
    },
    {
      id: 2,
      name: 'María',
      surname: 'García',
      profilePicture: null,
      role: 'ADMIN',
      lastMessage: 'Buenos días',
      lastMessageAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(),
      unreadCount: 0
    }
  ];

  beforeEach(async () => {
    const chatSpy = jasmine.createSpyObj('ChatService', [
      'getContacts', 'getConversation', 'getNewMessages', 'sendMessage',
      'sendAttachment', 'markAsRead', 'getUnreadTotal'
    ]);
    chatSpy.getContacts.and.returnValue(of(mockContacts));
    chatSpy.getConversation.and.returnValue(of([]));
    chatSpy.markAsRead.and.returnValue(of({}));

    const pushSpy = jasmine.createSpyObj('FirebasePushService', ['requestPermissionAndGetToken', 'showPushNotification']);

    localStorage.setItem('currentUser', JSON.stringify({ id: 99, name: 'Test User' }));

    await TestBed.configureTestingModule({
      imports: [
        Chats,
        HttpClientTestingModule,
        FormsModule
      ],
      providers: [
        { provide: ChatService, useValue: chatSpy },
        { provide: FirebasePushService, useValue: pushSpy }
      ]
    }).compileComponents();

    chatServiceSpy = TestBed.inject(ChatService) as jasmine.SpyObj<ChatService>;
    fixture = TestBed.createComponent(Chats);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    localStorage.removeItem('currentUser');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should load contacts on init', () => {
    expect(chatServiceSpy.getContacts).toHaveBeenCalledWith(99);
    expect(component.chats.length).toBe(2);
  });

  it('should filter chats by search term', () => {
    component.searchTerm = 'Juan';
    component.applyFilter();

    expect(component.filteredChats.length).toBe(1);
    expect(component.filteredChats[0].name).toBe('Juan');
  });

  it('should select a chat and load its conversation', () => {
    const chat = component.chats[0];
    component.selectChat(chat);

    expect(component.selectedChat).toBe(chat);
    expect(chatServiceSpy.getConversation).toHaveBeenCalledWith(99, chat.id);
    expect(chatServiceSpy.markAsRead).toHaveBeenCalledWith(99, chat.id);
  });

  it('should close chat', () => {
    component.selectChat(component.chats[0]);
    component.closeChat();

    expect(component.selectedChat).toBeNull();
    expect(component.selectedChatMessages.length).toBe(0);
  });

  it('should get correct initials', () => {
    expect(component.getInitials('Juan', 'Pérez')).toBe('JP');
    expect(component.getInitials('María', 'García')).toBe('MG');
  });

  it('should format time correctly', () => {
    const now = new Date();
    expect(component.formatTimeAgo(now.toISOString())).toBe('Ahora');

    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60 * 1000);
    expect(component.formatTimeAgo(fiveMinutesAgo.toISOString())).toBe('5m');
  });

  it('should format message time', () => {
    const date = new Date('2024-01-15T14:30:00');
    const result = component.formatMessageTime(date.toISOString());
    expect(result).toContain(':');
  });

  it('should get correct image URL', () => {
    expect(component.getCorrectImageUrl(null)).toBe('');
    expect(component.getCorrectImageUrl(undefined)).toBe('');
    expect(component.getCorrectImageUrl('http://example.com/image.jpg')).toBe('http://example.com/image.jpg');
    expect(component.getCorrectImageUrl('/uploads/image.jpg')).toBe('http://localhost:8080/uploads/image.jpg');
  });

  it('should track chats by id', () => {
    expect(component.trackByChatId(0, component.chats[0])).toBe(1);
  });

  it('should not send empty message', () => {
    component.selectChat(component.chats[0]);
    component.newMessage = '   ';

    const initialLength = component.selectedChatMessages.length;
    component.sendMessage();

    expect(component.selectedChatMessages.length).toBe(initialLength);
  });

  it('should render the built-in sticker pack', () => {
    expect(component.stickerPack.length).toBeGreaterThan(0);
  });
});