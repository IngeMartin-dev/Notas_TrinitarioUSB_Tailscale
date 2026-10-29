package com.notastrinitario.app.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * Mensaje del chat interno (estilo WhatsApp) entre profesores, directores de
 * grupo y administradores. Los padres de familia nunca aparecen como
 * remitentes/destinatarios posibles de este chat (se filtra en
 * ChatController), aunque sí pueden abrir la sección para escribirle a un
 * profesor/administrador.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_sender_receiver", columnList = "sender_id, receiver_id"),
        @Index(name = "idx_chat_receiver_sender", columnList = "receiver_id, sender_id"),
        @Index(name = "idx_chat_created_at", columnList = "created_at")
})
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum MessageType {
        TEXT, IMAGE, FILE, GIF, STICKER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private MessageType type = MessageType.TEXT;

    @Column(columnDefinition = "TEXT")
    private String content; // texto del mensaje, o URL/emoji del sticker/GIF si type != TEXT

    @Column(name = "file_url")
    private String fileUrl; // ruta pública /uploads/chat/... para IMAGE/FILE

    @Column(name = "file_name")
    private String fileName; // nombre original del archivo (FILE)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "deleted_for_all")
    private boolean deletedForAll = false;

    public ChatMessage() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getSender() { return sender; }
    public void setSender(User sender) { this.sender = sender; }

    public User getReceiver() { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public boolean isDeletedForAll() { return deletedForAll; }
    public void setDeletedForAll(boolean deletedForAll) { this.deletedForAll = deletedForAll; }
}