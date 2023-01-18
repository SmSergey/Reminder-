package com.smirnov.app.domain.reminder;

import com.smirnov.app.common.exceptions.ForbiddenException;
import com.smirnov.app.domain.reminder.dto.CreateReminderRequestDto;
import com.smirnov.app.domain.reminder.dto.DeleteReminderRequestDto;
import com.smirnov.app.domain.reminder.dto.ListRemindersResponseDto;
import com.smirnov.app.domain.reminder.dto.UpdateReminderRequestDto;
import com.smirnov.app.domain.user.User;
import com.smirnov.app.domain.user.UserRepository;
import com.smirnov.app.domain.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReminderController {

    private final UserRepository userRepository;
    private final ReminderRepository reminderRepository;

    private final ReminderService reminderService;
    private final UserService userService;


    @GetMapping("/list")
    public ResponseEntity<ListRemindersResponseDto> getAllReminders(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            OAuth2AuthenticationToken token,
            @PageableDefault Pageable pageRequest
    ) {
        final String phoneNumber = userService.getPhoneNumber(authorizedClient.getAccessToken());
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(phoneNumber, email)));

        Page<Reminder> pageReminders = reminderRepository.findByOwner(owner, pageRequest);
        ListRemindersResponseDto response = new ListRemindersResponseDto(pageReminders);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/reminder/create")
    public ResponseEntity<Long> createReminder(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            @RequestBody CreateReminderRequestDto createReminderDto,
            OAuth2AuthenticationToken token
    ) {
        final String phoneNumber = userService.getPhoneNumber(authorizedClient.getAccessToken());
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(phoneNumber, email)));

        //need to rewrite phone number to keep it in actual state
        owner.setPhone(phoneNumber);

        Reminder createdReminder = reminderService.createReminder(createReminderDto, owner);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdReminder.getId());
    }

    @DeleteMapping("/reminder/delete")
    public ResponseEntity<Long> deleteReminder(
            OAuth2AuthenticationToken token,
            @RequestBody DeleteReminderRequestDto dto
    ) {
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("user don't have such reminder"));

        Reminder reminder = reminderRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("reminder with this id wasn't found"));

        if (!owner.getId().equals(reminder.getOwner().getId())) {
            throw new ForbiddenException("you are not an owner of this reminder");
        }

        reminderRepository.deleteById(dto.getId());

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT).build();
    }

    @PatchMapping("/reminder/update")
    public ResponseEntity<Reminder> updateEntity(
            OAuth2AuthenticationToken token,
            @RequestBody UpdateReminderRequestDto dto
    ) {

        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("user don't have such reminder"));

        Reminder reminder = reminderRepository.findById(dto.getId())
                .orElseThrow(() -> new EntityNotFoundException("reminder with this id wasn't found"));

        if (!owner.getId().equals(reminder.getOwner().getId())) {
            throw new ForbiddenException("you are not an owner of this reminder");
        }

        reminderRepository.save(reminder.mergeWithUpdateDto(dto));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(reminder);
    }

    @GetMapping("/search")
    public ResponseEntity<ListRemindersResponseDto> searchReminder(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            OAuth2AuthenticationToken token,
            @RequestParam("query") String query,
            Pageable pageable
    ) {
        final String phoneNumber = userService.getPhoneNumber(authorizedClient.getAccessToken());
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(phoneNumber, email)));

        Page<Reminder> pageReminders = reminderRepository.findByQuery(owner.getId(), query, pageable);
        ListRemindersResponseDto response = new ListRemindersResponseDto(pageReminders);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/sort")
    public ResponseEntity<List<Reminder>> getSortedReminders(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            OAuth2AuthenticationToken token,
            PageRequest pageRequest
    ) {
        final String phoneNumber = userService.getPhoneNumber(authorizedClient.getAccessToken());
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(phoneNumber, email)));

        List<Reminder> filteredReminders = reminderRepository.findByOwner(owner, pageRequest).getContent();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(filteredReminders);
    }


    @GetMapping("/filtr")
    public ResponseEntity<List<Reminder>> getFilteredReminders(
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient,
            OAuth2AuthenticationToken token,
            @RequestParam(name = "fromDate", required = false, defaultValue = "1990-00-00") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false, defaultValue = "9999-12-00") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "fromTime", required = false, defaultValue = "00:00:00") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME, pattern = "HH:mm:ss") LocalTime fromTime,
            @RequestParam(name = "toTime", required = false, defaultValue = "23:59:59") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME, pattern = "HH:mm:ss") LocalTime toTime
    ) {
        final String phoneNumber = userService.getPhoneNumber(authorizedClient.getAccessToken());
        final String email = token.getPrincipal().getAttribute("email");

        User owner = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(new User(phoneNumber, email)));

        List<Reminder> filteredReminders = reminderRepository.findRemindersFilteredByDateAndTime(
                owner.getId(),
                fromDate.atStartOfDay(),
                toDate.atStartOfDay(),
                fromTime,
                toTime
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(filteredReminders);
    }
}
