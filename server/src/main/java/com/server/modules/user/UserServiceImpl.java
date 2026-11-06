package com.server.modules.user;

import com.server.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void register(String phone, String fullName, String password) {
        String e164 = Phones.toE164(phone);
        if (userRepository.existsByPhone(e164)) {
            throw new BusinessException(UserMessages.PHONE_EXISTS);
        }
        User user = new User();
        user.setPhone(e164);
        user.setFullName(fullName.trim());
        user.setPassword(passwordEncoder.encode(password));
        userRepository.saveAndFlush(user);
    }
}
