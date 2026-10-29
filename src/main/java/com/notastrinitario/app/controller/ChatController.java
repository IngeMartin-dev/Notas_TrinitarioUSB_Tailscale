package com.notastrinitario.app.controller;

import com.notastrinitario.app.entity.ChatMessage;
import com.notastrinitario.app.entity.Student;
import com.notastrinitario.app.entity.Subject;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.ChatMessageRepository;
import com.notastrinitario.app.repository.HomeroomAssignmentRepository;
import com.notastrinitario.app.repository.StudentRepository;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.service.FcmPushService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Chat interno estilo WhatsApp entre profesores, directores de grupo,
 * administradores Y padres/estudiantes.
 *
 * - El personal (ADMIN / TEACHER / DIRECTOR_DE_GRUPO) ve en su directorio al
 *   resto del personal, MÁS cualquier padre que ya le haya escrito (para
 *   poder verlo y responderle).
 * - Un padre/estudiante (rol PARENT) ve en su directorio a TODOS los
 *   profesores que dictan clase en el grado de su(s) hijo(s), al director de
 *   grupo de ese grado/salón, y a todos los administradores. Nunca ve a
 *   otros padres.
 *
 * Nota sobre "tiempo real": los mensajes se guardan al instante en la base
 * de datos y se dispara una notificación push (FCM) de inmediato al
 * destinatario. Mientras el chat está abierto en pantalla, el frontend hace
 * "polling" cada pocos segundos para traer mensajes nuevos (ver
 * chat.service.ts). Esto evita tener que montar infraestructura de
 * WebSocket/STOMP con autenticación JWT sobre el socket, que es bastante más
 * delicada de dejar funcionando a la primera sin poder probarla contra tu
 * servidor real.
 */
@RestController
@RequestMapping("/api/chats")
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','DIRECTOR_DE_GRUPO','PARENT')")
public class ChatController {

    private static final String CHAT_UPLOAD_DIR = "uploads/chat";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Tipos de archivo permitidos como adjunto (imagen o documento razonable).
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"
    );
    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024; // 15 MB

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final FcmPushService fcmPushService;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;
    private final HomeroomAssignmentRepository homeroomAssignmentRepository;

    public ChatController(ChatMessageRepository chatMessageRepository,
                           UserRepository userRepository,
                           FcmPushService fcmPushService,
                           StudentRepository studentRepository,
                           SubjectRepository subjectRepository,
                           HomeroomAssignmentRepository homeroomAssignmentRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.fcmPushService = fcmPushService;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
        this.homeroomAssignmentRepository = homeroomAssignmentRepository;
    }

    private int parseGradeNumber(String grade) {
        if (grade == null) return 0;
        String num = grade.replaceAll("[^0-9]", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    /** Profesores + director de grupo relevantes para los hijos de un padre,
     *  más todos los administradores. Sin duplicados. */
    private List<User> contactsForParent(Long parentUserId) {
        List<Student> children = studentRepository.findByParentId(parentUserId);
        Map<Long, User> byId = new LinkedHashMap<>();

        // Todos los administradores, siempre.
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != null && "ADMIN".equalsIgnoreCase(u.getRole().getName()))
                .forEach(u -> byId.put(u.getId(), u));

        for (Student child : children) {
            int gradeNum = parseGradeNumber(child.getGrade());
            if (gradeNum > 0) {
                // Profesores de cualquier materia asignada a ese grado.
                for (Subject subject : subjectRepository.findByGradeRange(gradeNum)) {
                    User teacher = subject.getTeacher();
                    if (teacher != null) {
                        byId.put(teacher.getId(), teacher);
                    }
                }
            }
            // Director de grupo del grado/salón del estudiante.
            if (child.getGrade() != null && child.getClassGroup() != null) {
                homeroomAssignmentRepository.findByGradeAndClassroom(child.getGrade(), child.getClassGroup())
                        .map(a -> a.getUser())
                        .filter(Objects::nonNull)
                        .ifPresent(u -> byId.put(u.getId(), u));
            }
        }

        return new ArrayList<>(byId.values());
    }

    // ── Directorio de contactos ─────────────────────────────────────────
    @GetMapping("/contacts")
    public ResponseEntity<?> getContacts(@RequestParam Long currentUserId) {
        if (!esUsuarioValido(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        User currentUser = userRepository.findById(currentUserId).orElse(null);
        String currentRole = currentUser != null && currentUser.getRole() != null
                ? currentUser.getRole().getName() : "";

        List<User> users;
        if ("PARENT".equalsIgnoreCase(currentRole)) {
            users = contactsForParent(currentUserId);
        } else {
            // Personal: el resto del personal, MÁS los padres que ya le hayan
            // escrito a este usuario (para poder verlos y responderles).
            Set<Long> parentIdsWhoWrote = chatMessageRepository.findDistinctSenderIdsTo(currentUserId);
            users = userRepository.findAll().stream()
                    .filter(u -> u.getRole() != null)
                    .filter(u -> {
                        String role = u.getRole().getName();
                        boolean isStaff = "ADMIN".equalsIgnoreCase(role) || "TEACHER".equalsIgnoreCase(role)
                                || "DIRECTOR_DE_GRUPO".equalsIgnoreCase(role);
                        boolean isParentWhoWrote = "PARENT".equalsIgnoreCase(role) && parentIdsWhoWrote.contains(u.getId());
                        return isStaff || isParentWhoWrote;
                    })
                    .collect(Collectors.toList());
        }

        users = users.stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .sorted(Comparator.comparing(User::getSurname, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(User::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = new ArrayList<>();
        for (User u : users) {
            Map<String, Object> contact = new LinkedHashMap<>();
            contact.put("id", u.getId());
            contact.put("name", u.getName());
            contact.put("surname", u.getSurname());
            contact.put("profilePicture", u.getProfilePicture());
            contact.put("role", u.getRole().getName());

            List<ChatMessage> conversation = chatMessageRepository.findConversation(currentUserId, u.getId());
            if (!conversation.isEmpty()) {
                ChatMessage last = conversation.get(conversation.size() - 1);
                contact.put("lastMessage", previewOf(last));
                contact.put("lastMessageAt", last.getCreatedAt().format(ISO));
            } else {
                contact.put("lastMessage", null);
                contact.put("lastMessageAt", null);
            }
            long unread = chatMessageRepository.countUnreadFrom(currentUserId, u.getId());
            contact.put("unreadCount", unread);

            result.add(contact);
        }

        // Contactos con conversación reciente primero.
        result.sort((a, b) -> {
            Object dateA = a.get("lastMessageAt");
            Object dateB = b.get("lastMessageAt");
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return ((String) dateB).compareTo((String) dateA);
        });

        return ResponseEntity.ok(result);
    }

    // ── Historial de conversación con un contacto ───────────────────────
    @GetMapping("/conversation/{otherUserId}")
    public ResponseEntity<?> getConversation(@RequestParam Long currentUserId, @PathVariable Long otherUserId) {
        if (!esUsuarioValido(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        List<ChatMessage> messages = chatMessageRepository.findConversation(currentUserId, otherUserId);
        return ResponseEntity.ok(messages.stream().map(this::toDto).collect(Collectors.toList()));
    }

    // ── Mensajes nuevos desde cierto instante (polling del chat abierto) ─
    @GetMapping("/conversation/{otherUserId}/nuevos")
    public ResponseEntity<?> getNewMessages(@RequestParam Long currentUserId,
                                             @PathVariable Long otherUserId,
                                             @RequestParam String since) {
        if (!esUsuarioValido(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        LocalDateTime desde;
        try {
            desde = LocalDateTime.parse(since, ISO);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Formato de fecha inválido"));
        }
        List<ChatMessage> messages = chatMessageRepository.findNewSince(currentUserId, otherUserId, desde);
        return ResponseEntity.ok(messages.stream().map(this::toDto).collect(Collectors.toList()));
    }

    // ── Enviar mensaje de texto ──────────────────────────────────────────
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> body) {
        try {
            // El senderId SIEMPRE se toma del usuario autenticado (JWT), no
            // del "senderId" que manda el cliente en el body: antes se
            // confiaba en ese valor tal cual, así que cualquiera podía
            // enviar un mensaje haciéndose pasar por otro remitente.
            Long senderId = currentUserIdOrThrow();
            Long receiverId = Long.valueOf(body.get("receiverId").toString());
            String content = (String) body.get("content");
            String typeRaw = (String) body.getOrDefault("type", "TEXT");

            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El mensaje no puede estar vacío"));
            }

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Remitente no encontrado"));
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new RuntimeException("Destinatario no encontrado"));

            if (!esParEnviarioValido(sender, receiver)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No se puede enviar mensajes a este usuario"));
            }

            ChatMessage msg = new ChatMessage();
            msg.setSender(sender);
            msg.setReceiver(receiver);
            msg.setType(parseType(typeRaw));
            msg.setContent(content.trim());
            msg.setCreatedAt(LocalDateTime.now());

            ChatMessage saved = chatMessageRepository.save(msg);
            enviarPush(sender, receiver, saved);

            return ResponseEntity.ok(toDto(saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Enviar adjunto: imagen, archivo, GIF o sticker subido como imagen ─
    @PostMapping("/messages/adjunto")
    public ResponseEntity<?> sendAttachment(@RequestParam Long senderId,
                                             @RequestParam Long receiverId,
                                             @RequestParam String type,
                                             @RequestParam("file") MultipartFile file) {
        try {
            if (!esUsuarioValido(senderId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
            }
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Archivo vacío"));
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                return ResponseEntity.badRequest().body(Map.of("error", "El archivo supera el máximo de 15 MB"));
            }

            String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "archivo";
            String extension = obtenerExtension(originalName).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Tipo de archivo no permitido: " + extension));
            }

            User sender = userRepository.findById(senderId)
                    .orElseThrow(() -> new RuntimeException("Remitente no encontrado"));
            User receiver = userRepository.findById(receiverId)
                    .orElseThrow(() -> new RuntimeException("Destinatario no encontrado"));

            if (!esParEnviarioValido(sender, receiver)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "No se puede enviar mensajes a este usuario"));
            }

            Path uploadDir = Paths.get(CHAT_UPLOAD_DIR);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String safeName = System.currentTimeMillis() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = uploadDir.resolve(safeName);
            Files.write(filePath, file.getBytes());

            ChatMessage msg = new ChatMessage();
            msg.setSender(sender);
            msg.setReceiver(receiver);
            msg.setType(parseType(type));
            msg.setFileUrl("/uploads/chat/" + safeName);
            msg.setFileName(originalName);
            msg.setContent(msg.getType() == ChatMessage.MessageType.IMAGE ? "📷 Foto"
                    : msg.getType() == ChatMessage.MessageType.GIF ? "GIF"
                    : msg.getType() == ChatMessage.MessageType.STICKER ? "Sticker"
                    : "📎 " + originalName);
            msg.setCreatedAt(LocalDateTime.now());

            ChatMessage saved = chatMessageRepository.save(msg);
            enviarPush(sender, receiver, saved);

            return ResponseEntity.ok(toDto(saved));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No se pudo guardar el archivo: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Marcar como leída toda la conversación con un contacto ──────────
    @PutMapping("/conversation/{otherUserId}/leido")
    public ResponseEntity<?> markAsRead(@RequestParam Long currentUserId, @PathVariable Long otherUserId) {
        if (!esUsuarioValido(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        List<ChatMessage> unread = chatMessageRepository.findUnreadFrom(currentUserId, otherUserId);
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(m -> m.setReadAt(now));
        chatMessageRepository.saveAll(unread);
        return ResponseEntity.ok(Map.of("marcados", unread.size()));
    }

    // ── Total de mensajes sin leer (para el badge del menú) ─────────────
    @GetMapping("/no-leidos")
    public ResponseEntity<?> unreadTotal(@RequestParam Long currentUserId) {
        if (!esUsuarioValido(currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No autorizado"));
        }
        long total = chatMessageRepository.countUnreadTotal(currentUserId);
        return ResponseEntity.ok(Map.of("total", total));
    }

    // ═══════════════════════ helpers ════════════════════════════════════

    /**
     * Antes, "currentUserId" y "senderId" en este controlador se tomaban
     * tal cual del query param / body que mandaba el cliente, sin verificar
     * nada: cualquiera podía leer o marcar como leídas las conversaciones
     * privadas de otra persona con solo cambiar ese número en la URL, o
     * enviar mensajes haciéndose pasar por otro remitente. Estos helpers
     * obligan a que ese id coincida con el usuario autenticado por JWT (o
     * que sea ADMIN).
     */
    private User currentAuthUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Object principal = auth != null ? auth.getPrincipal() : null;
        return (principal instanceof User) ? (User) principal : null;
    }

    private Long currentUserIdOrThrow() {
        User u = currentAuthUser();
        if (u == null) throw new RuntimeException("No autenticado");
        return u.getId();
    }

    private boolean esAdminActual() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /** true si claimedUserId es realmente el usuario autenticado, o si quien llama es ADMIN. */
    private boolean esUsuarioValido(Long claimedUserId) {
        User u = currentAuthUser();
        if (u == null) return false;
        return esAdminActual() || u.getId().equals(claimedUserId);
    }

    /** Un par sender/receiver es válido si: ambos son personal (ADMIN,
     *  TEACHER, DIRECTOR_DE_GRUPO), o uno es personal y el otro es un padre
     *  (PARENT). Nunca se permite padre-a-padre. */
    private boolean esParEnviarioValido(User sender, User receiver) {
        if (sender.getRole() == null || receiver.getRole() == null) return false;
        String senderRole = sender.getRole().getName();
        String receiverRole = receiver.getRole().getName();
        boolean senderIsStaff = isStaffRole(senderRole);
        boolean receiverIsStaff = isStaffRole(receiverRole);
        boolean senderIsParent = "PARENT".equalsIgnoreCase(senderRole);
        boolean receiverIsParent = "PARENT".equalsIgnoreCase(receiverRole);
        return (senderIsStaff && receiverIsStaff)
                || (senderIsStaff && receiverIsParent)
                || (senderIsParent && receiverIsStaff);
    }

    private boolean isStaffRole(String role) {
        return "ADMIN".equalsIgnoreCase(role) || "TEACHER".equalsIgnoreCase(role)
                || "DIRECTOR_DE_GRUPO".equalsIgnoreCase(role);
    }

    private ChatMessage.MessageType parseType(String raw) {
        try {
            return ChatMessage.MessageType.valueOf(raw == null ? "TEXT" : raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatMessage.MessageType.TEXT;
        }
    }

    private String obtenerExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx >= 0 && idx < filename.length() - 1 ? filename.substring(idx + 1) : "";
    }

    private String previewOf(ChatMessage m) {
        return switch (m.getType()) {
            case IMAGE -> "📷 Foto";
            case FILE -> "📎 " + (m.getFileName() != null ? m.getFileName() : "Archivo");
            case GIF -> "GIF";
            case STICKER -> "Sticker";
            default -> m.getContent();
        };
    }

    private void enviarPush(User sender, User receiver, ChatMessage msg) {
        try {
            String title = (sender.getName() != null ? sender.getName() : "") + " "
                    + (sender.getSurname() != null ? sender.getSurname() : "");
            fcmPushService.sendToUser(receiver.getId(), title.trim(), previewOf(msg), "CHAT_MESSAGE");
        } catch (Exception ignored) {
            // Un fallo enviando el push nunca debe tumbar el envío del mensaje.
        }
    }

    private Map<String, Object> toDto(ChatMessage m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("senderId", m.getSender().getId());
        dto.put("senderName", (m.getSender().getName() + " " + m.getSender().getSurname()).trim());
        dto.put("receiverId", m.getReceiver().getId());
        dto.put("type", m.getType().name());
        dto.put("content", m.getContent());
        dto.put("fileUrl", m.getFileUrl());
        dto.put("fileName", m.getFileName());
        dto.put("createdAt", m.getCreatedAt().format(ISO));
        dto.put("read", m.getReadAt() != null);
        return dto;
    }
}