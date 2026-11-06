package com.server.modules.user;

import com.server.common.exception.BusinessException;
import com.server.modules.auth.dto.RegisterRequest;
import com.server.modules.key.KeyService;
import com.server.modules.verification.VerificationPurpose;
import com.server.modules.verification.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationService verificationService;
    private final KeyService keyService;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String phone = Phones.toE164(request.phone());
        // Kayıtlı numaraya kayıt kodu gönderilmez; kodu bilen biri buraya ancak kayıtsız bir numarayla gelebilir.
        verificationService.verify(phone, VerificationPurpose.REGISTER, request.code());
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException(UserMessages.PHONE_EXISTS);
        }
        User user = new User();
        user.setPhone(phone);
        user.setFullName(request.fullName().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.saveAndFlush(user);
        keyService.create(user.getId(), request.keys());
    }
}
