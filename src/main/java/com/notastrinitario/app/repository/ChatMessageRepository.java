package com.notastrinitario.app.repository;

import com.notastrinitario.app.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // Conversación completa entre dos usuarios (en cualquier dirección), ordenada por fecha.
    @Query("SELECT m FROM ChatMessage m WHERE m.deletedForAll = false AND " +
           "((m.sender.id = :userA AND m.receiver.id = :userB) OR " +
           " (m.sender.id = :userB AND m.receiver.id = :userA)) " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findConversation(@Param("userA") Long userA, @Param("userB") Long userB);

    // Último mensaje de cada conversación en la que participa un usuario (para la lista de contactos).
    @Query("SELECT m FROM ChatMessage m WHERE m.deletedForAll = false AND " +
           "(m.sender.id = :userId OR m.receiver.id = :userId) ORDER BY m.createdAt DESC")
    List<ChatMessage> findAllForUserOrderedDesc(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.receiver.id = :userId AND m.sender.id = :otherId " +
           "AND m.readAt IS NULL AND m.deletedForAll = false")
    long countUnreadFrom(@Param("userId") Long userId, @Param("otherId") Long otherId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.receiver.id = :userId " +
           "AND m.readAt IS NULL AND m.deletedForAll = false")
    long countUnreadTotal(@Param("userId") Long userId);

    @Query("SELECT m FROM ChatMessage m WHERE m.receiver.id = :userId AND m.sender.id = :otherId " +
           "AND m.readAt IS NULL AND m.deletedForAll = false")
    List<ChatMessage> findUnreadFrom(@Param("userId") Long userId, @Param("otherId") Long otherId);

    // Mensajes nuevos desde cierto instante, para el "polling" del chat abierto.
    @Query("SELECT m FROM ChatMessage m WHERE m.deletedForAll = false AND " +
           "((m.sender.id = :userA AND m.receiver.id = :userB) OR " +
           " (m.sender.id = :userB AND m.receiver.id = :userA)) AND m.createdAt > :since " +
           "ORDER BY m.createdAt ASC")
    List<ChatMessage> findNewSince(@Param("userA") Long userA, @Param("userB") Long userB,
                                    @Param("since") LocalDateTime since);

    /** IDs distintos de usuarios que le han escrito (como remitentes) a
     *  :userId al menos una vez. Se usa para que el personal (profesores,
     *  directores, admins) vea en su directorio de contactos a los padres
     *  que ya les escribieron, aunque los padres no formen parte del
     *  directorio "base" de personal. */
    @Query("SELECT DISTINCT m.sender.id FROM ChatMessage m WHERE m.receiver.id = :userId")
    java.util.Set<Long> findDistinctSenderIdsTo(@Param("userId") Long userId);
}