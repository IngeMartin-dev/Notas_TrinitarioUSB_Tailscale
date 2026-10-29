package com.notastrinitario.app.service;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.notastrinitario.app.entity.User;

public interface UserService {

	public List<User> findAll();
	
	public Page<User> findAll(Pageable pageable);
	
    public Optional<User> findById(Long id);
	
    public Optional<User> findByUsername(String username);
	
    public Optional<User> findByEmail(String email);
	
    public User save(User user);
	
	public void deleteById(Long id);

	// Autenticación de Dos Factores
	public String generate2faSecret(Long userId);
	
	public boolean enable2fa(Long userId, String code);
	
	public boolean disable2fa(Long userId, String password);
	
	public boolean verify2faCode(Long userId, String code);
	
	public String generateLogin2faCode(Long userId);
	
	public boolean verifyLogin2faCode(Long userId, String code);
	
	public Optional<User> findByTemp2faCode(String code);

	public List<User> findByRoleName(String roleName);

	public void changePassword(Long userId, String currentPassword, String newPassword);

	public void resetPassword(Long userId, String newPassword);
}
