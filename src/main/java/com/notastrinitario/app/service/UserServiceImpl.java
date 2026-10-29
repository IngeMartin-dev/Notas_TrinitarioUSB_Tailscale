package com.notastrinitario.app.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.notastrinitario.app.entity.User;
import com.notastrinitario.app.repository.UserRepository;
import com.notastrinitario.app.repository.FcmTokenRepository;
import com.notastrinitario.app.repository.RefreshTokenRepository;
import com.notastrinitario.app.repository.NotificationRepository;
import com.notastrinitario.app.repository.ParentClassRepository;
import com.notastrinitario.app.repository.HomeroomAssignmentRepository;
import com.notastrinitario.app.repository.ReportCardHistoryRepository;
import com.notastrinitario.app.repository.DigitalSignatureRepository;
import com.notastrinitario.app.repository.ReportCardRepository;
import com.notastrinitario.app.repository.SubjectRepository;
import com.notastrinitario.app.repository.RecoveryPlanRepository;
import com.notastrinitario.app.repository.RecoveryDataRepository;
import com.notastrinitario.app.repository.SubjectGradeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class UserServiceImpl implements UserService{
    
    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final NotificationRepository notificationRepository;
    private final ParentClassRepository parentClassRepository;
    private final HomeroomAssignmentRepository homeroomAssignmentRepository;
    private final ReportCardHistoryRepository reportCardHistoryRepository;
    private final DigitalSignatureRepository digitalSignatureRepository;
    private final ReportCardRepository reportCardRepository;
    private final SubjectRepository subjectRepository;
    private final RecoveryPlanRepository recoveryPlanRepository;
    private final RecoveryDataRepository recoveryDataRepository;
    private final SubjectGradeRepository subjectGradeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UserServiceImpl(UserRepository userRepository,
                           FcmTokenRepository fcmTokenRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           NotificationRepository notificationRepository,
                           ParentClassRepository parentClassRepository,
                           HomeroomAssignmentRepository homeroomAssignmentRepository,
                           ReportCardHistoryRepository reportCardHistoryRepository,
                           DigitalSignatureRepository digitalSignatureRepository,
                           ReportCardRepository reportCardRepository,
                           SubjectRepository subjectRepository,
                           RecoveryPlanRepository recoveryPlanRepository,
                           RecoveryDataRepository recoveryDataRepository,
                           SubjectGradeRepository subjectGradeRepository) {
        this.userRepository = userRepository;
        this.fcmTokenRepository = fcmTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.notificationRepository = notificationRepository;
        this.parentClassRepository = parentClassRepository;
        this.homeroomAssignmentRepository = homeroomAssignmentRepository;
        this.reportCardHistoryRepository = reportCardHistoryRepository;
        this.digitalSignatureRepository = digitalSignatureRepository;
        this.reportCardRepository = reportCardRepository;
        this.subjectRepository = subjectRepository;
        this.recoveryPlanRepository = recoveryPlanRepository;
        this.recoveryDataRepository = recoveryDataRepository;
        this.subjectGradeRepository = subjectGradeRepository;
    }
	
	@Override
	@Transactional(readOnly = true) 
	public List<User> findAll() {
		return userRepository.findAll();
	}

	@Override
	@Transactional(readOnly = true) 
	public Page<User> findAll(Pageable pageable) {
		return userRepository.findAll(pageable);
	}

	@Override
	@Transactional(readOnly = true) 
	public Optional<User> findById(Long id) {
		
		return userRepository.findById(id);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<User> findByUsername(String username) {
		return userRepository.findByUsername(username);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<User> findByEmail(String email) {
		return userRepository.findByEmail(email);
	}

	@Override
	@Transactional
	public User save(User user) {
		
		return userRepository.save(user);
	}

	@Override
	@Transactional
	public void deleteById(Long id) {
		Optional<User> userOpt = userRepository.findById(id);
		if (userOpt.isEmpty()) {
			return;
		}

		// 1) Quitar al usuario de la relación muchos-a-muchos con estudiantes
		entityManager.createNativeQuery("DELETE FROM student_parents WHERE user_id = :id")
				.setParameter("id", id).executeUpdate();

		// 2) Eliminar registros hijos que referencian al usuario
		fcmTokenRepository.deleteByUserId(id);
		refreshTokenRepository.deleteByUserId(id);
		notificationRepository.deleteByUserId(id);
		parentClassRepository.deleteByUserId(id);
		homeroomAssignmentRepository.deleteByUserId(id);
		reportCardHistoryRepository.deleteByUserId(id);
		digitalSignatureRepository.deleteByUserId(id);

		// 3) Desvincular referencias de docente/director (dejar registros intactos)
		reportCardRepository.clearTeacherFromReportCards(id);
		reportCardRepository.clearCreatedByFromReportCards(id);
		subjectRepository.clearTeacherFromSubjects(id);
		recoveryPlanRepository.clearTeacherFromRecoveryPlans(id);
		recoveryDataRepository.clearTeacherFromRecoveryData(id);
		subjectGradeRepository.clearTeacherFromSubjectGrades(id);

		// 4) Finalmente eliminar el usuario
		userRepository.deleteById(id);
	}

	@Override
	@Transactional
	public String generate2faSecret(Long userId) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return null;
		}
		
		User user = userOpt.get();
		SecureRandom random = new SecureRandom();
		byte[] secretBytes = new byte[20];
		random.nextBytes(secretBytes);
		String secret = Base64.getEncoder().encodeToString(secretBytes);
		
		user.setTwoFactorSecret(secret);
		userRepository.save(user);
		
		return secret;
	}

	@Override
	@Transactional
	public boolean enable2fa(Long userId, String code) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return false;
		}
		
		User user = userOpt.get();
		
		if (!verify2faCode(userId, code)) {
			return false;
		}
		
		user.setTwoFactorEnabled(true);
		userRepository.save(user);
		
		return true;
	}

	@Override
	@Transactional
	public boolean disable2fa(Long userId, String password) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return false;
		}
		
		User user = userOpt.get();
		
		user.setTwoFactorEnabled(false);
		user.setTwoFactorSecret(null);
		userRepository.save(user);
		
		return true;
	}

	@Override
	@Transactional
	public boolean verify2faCode(Long userId, String code) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return false;
		}
		
		User user = userOpt.get();
		String secret = user.getTwoFactorSecret();
		
		if (secret == null || code == null) {
			return false;
		}
		
		return validateSimpleCode(secret, code);
	}
	
	private boolean validateSimpleCode(String secret, String inputCode) {
		long currentTime = System.currentTimeMillis() / 1000;
		
		for (long offset = -1; offset <= 1; offset++) {
			long timeWindow = currentTime / 30 + offset;
			String expectedCode = generateCode(secret, timeWindow);
			if (expectedCode != null && expectedCode.equals(inputCode)) {
				return true;
			}
		}
		
		return false;
	}
	
	private String generateCode(String secret, long timeWindow) {
		try {
			String data = secret + ":" + timeWindow;
			byte[] hash = data.getBytes();
			
			int hashCode = 0;
			for (byte b : hash) {
				hashCode = ((hashCode << 5) - hashCode) + (b & 0xff);
				hashCode = hashCode & 0xffffffff;
			}
			
			int code = Math.abs(hashCode) % 1000000;
			return String.format("%06d", code);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	@Transactional
	public String generateLogin2faCode(Long userId) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return null;
		}
		
		User user = userOpt.get();
		
		SecureRandom random = new SecureRandom();
		int code = random.nextInt(900000) + 100000;
		String codeStr = String.valueOf(code);
		
		user.setTemp2faCode(codeStr);
		user.setTemp2faExpiry(System.currentTimeMillis() + (5 * 60 * 1000));
		userRepository.save(user);
		
		return codeStr;
	}

	@Override
	@Transactional
	public boolean verifyLogin2faCode(Long userId, String code) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			return false;
		}
		
		User user = userOpt.get();
		String storedCode = user.getTemp2faCode();
		Long expiry = user.getTemp2faExpiry();
		
		if (storedCode == null || expiry == null) {
			return false;
		}
		
		if (System.currentTimeMillis() > expiry) {
			user.setTemp2faCode(null);
			user.setTemp2faExpiry(null);
			userRepository.save(user);
			return false;
		}
		
		if (storedCode.equals(code)) {
			user.setTemp2faCode(null);
			user.setTemp2faExpiry(null);
			userRepository.save(user);
			return true;
		}
		
		return false;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<User> findByTemp2faCode(String code) {
		return userRepository.findByTemp2faCodeAndTemp2faExpiryGreaterThan(code, System.currentTimeMillis());
	}

	@Override
	@Transactional(readOnly = true)
	public List<User> findByRoleName(String roleName) {
		return userRepository.findByRoleName(roleName);
	}

	@Override
	@Transactional
	public void changePassword(Long userId, String currentPassword, String newPassword) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			throw new RuntimeException("Usuario no encontrado");
		}

		if (currentPassword == null || currentPassword.isEmpty()) {
			throw new RuntimeException("La contraseña actual es obligatoria");
		}

		User user = userOpt.get();
		String storedPassword = user.getPassword();

		boolean matches = passwordMatches(storedPassword, currentPassword);
		if (!matches) {
			throw new RuntimeException("La contraseña actual es incorrecta");
		}

		validateNewPassword(newPassword);

		user.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(newPassword));
		userRepository.save(user);
	}

	@Override
	@Transactional
	public void resetPassword(Long userId, String newPassword) {
		Optional<User> userOpt = userRepository.findById(userId);
		if (userOpt.isEmpty()) {
			throw new RuntimeException("Usuario no encontrado");
		}

		validateNewPassword(newPassword);

		User user = userOpt.get();
		user.setPassword(com.notastrinitario.app.security.PasswordSecurity.hash(newPassword));
		userRepository.save(user);
	}

	private boolean passwordMatches(String storedPassword, String currentPassword) {
		return com.notastrinitario.app.security.PasswordSecurity.matches(currentPassword, storedPassword);
	}

	private void validateNewPassword(String newPassword) {
		if (newPassword == null || newPassword.trim().length() < 6) {
			throw new RuntimeException("La nueva contraseña debe tener al menos 6 caracteres");
		}
	}

}
