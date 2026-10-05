package com.example.backend.master;

import static com.example.backend.master.InvestmentMasterDtos.*;

import com.example.backend.trade.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestmentMasterService {
    private final InvestmentMasterRepository repository;

    public InvestmentMasterService(InvestmentMasterRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BrokerageResponse> findBrokerages(
            String keyword, Boolean useYn, int page, int size) {
        return repository.findBrokerages(keyword, useYn, page, size);
    }

    @Transactional(readOnly = true)
    public BrokerageResponse findBrokerage(long brokerageId) {
        return repository.findBrokerageById(brokerageId)
                .orElseThrow(() -> notFound("증권사를 찾을 수 없습니다."));
    }

    @Transactional
    public BrokerageResponse createBrokerage(BrokerageRequest request) {
        BrokerageRequest normalized = normalizeBrokerage(request);
        validateUniqueBrokerageName(normalized.brokerageName(), null);
        return findSavedBrokerage(repository.insertBrokerage(normalized));
    }

    @Transactional
    public BrokerageResponse updateBrokerage(long brokerageId, BrokerageRequest request) {
        findBrokerage(brokerageId);
        BrokerageRequest normalized = normalizeBrokerage(request);
        validateUniqueBrokerageName(normalized.brokerageName(), brokerageId);
        if (repository.updateBrokerage(brokerageId, normalized) == 0) {
            throw notFound("증권사를 찾을 수 없습니다.");
        }
        return findSavedBrokerage(brokerageId);
    }

    @Transactional
    public BrokerageResponse updateBrokerageUsage(long brokerageId, UsageRequest request) {
        if (request == null || request.useYn() == null) {
            throw badRequest("사용 여부를 입력해 주세요.");
        }
        if (repository.updateBrokerageUsage(brokerageId, request.useYn()) == 0) {
            throw notFound("증권사를 찾을 수 없습니다.");
        }
        return findSavedBrokerage(brokerageId);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> findAccounts(
            Long brokerageId, String keyword, Boolean useYn, int page, int size) {
        return repository.findAccounts(brokerageId, keyword, useYn, page, size);
    }

    @Transactional(readOnly = true)
    public AccountResponse findAccount(long accountId) {
        return repository.findAccountById(accountId)
                .orElseThrow(() -> notFound("증권계좌를 찾을 수 없습니다."));
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        AccountRequest normalized = normalizeAccount(request);
        validateActiveBrokerage(normalized.brokerageId());
        return findSavedAccount(repository.insertAccount(normalized));
    }

    @Transactional
    public AccountResponse updateAccount(long accountId, AccountRequest request) {
        findAccount(accountId);
        AccountRequest normalized = normalizeAccount(request);
        validateActiveBrokerage(normalized.brokerageId());
        if (repository.updateAccount(accountId, normalized) == 0) {
            throw notFound("증권계좌를 찾을 수 없습니다.");
        }
        return findSavedAccount(accountId);
    }

    @Transactional
    public AccountResponse updateAccountUsage(long accountId, UsageRequest request) {
        if (request == null || request.useYn() == null) {
            throw badRequest("사용 여부를 입력해 주세요.");
        }
        AccountResponse account = findAccount(accountId);
        if (request.useYn()) validateActiveBrokerage(account.brokerageId());
        if (repository.updateAccountUsage(accountId, request.useYn()) == 0) {
            throw notFound("증권계좌를 찾을 수 없습니다.");
        }
        return findSavedAccount(accountId);
    }

    private BrokerageRequest normalizeBrokerage(BrokerageRequest request) {
        if (request == null || request.brokerageName() == null || request.brokerageName().isBlank()) {
            throw badRequest("증권사명을 입력해 주세요.");
        }
        String name = request.brokerageName().trim();
        if (name.length() > 100) throw badRequest("증권사명은 100자 이하로 입력해 주세요.");
        return new BrokerageRequest(name, request.useYn() == null || request.useYn());
    }

    private AccountRequest normalizeAccount(AccountRequest request) {
        if (request == null || request.brokerageId() == null) {
            throw badRequest("증권사를 선택해 주세요.");
        }
        if (request.accountName() == null || request.accountName().isBlank()) {
            throw badRequest("계좌 별칭을 입력해 주세요.");
        }
        String name = request.accountName().trim();
        if (name.length() > 100) throw badRequest("계좌 별칭은 100자 이하로 입력해 주세요.");
        String number = clean(request.accountNumber());
        if (number != null && number.length() > 100) {
            throw badRequest("계좌번호는 100자 이하로 입력해 주세요.");
        }
        return new AccountRequest(
                request.brokerageId(), name, number, clean(request.accountMemo()),
                request.useYn() == null || request.useYn());
    }

    private void validateUniqueBrokerageName(String name, Long excludedId) {
        if (repository.brokerageNameExists(name, excludedId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 등록된 증권사명입니다.");
        }
    }

    private void validateActiveBrokerage(long brokerageId) {
        if (!repository.activeBrokerageExists(brokerageId)) {
            throw badRequest("사용 중인 증권사만 계좌를 등록하거나 활성화할 수 있습니다.");
        }
    }

    private BrokerageResponse findSavedBrokerage(long brokerageId) {
        return repository.findBrokerageById(brokerageId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 증권사를 조회하지 못했습니다."));
    }

    private AccountResponse findSavedAccount(long accountId) {
        return repository.findAccountById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 계좌를 조회하지 못했습니다."));
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
