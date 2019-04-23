package com.example.delivery_service.controllers;

import com.example.delivery_service.model.Entity.User;
import com.example.delivery_service.model.UserDetailsImpl;
import com.example.delivery_service.services.RoleService;
import com.example.delivery_service.services.StateService;
import com.example.delivery_service.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.*;

@Controller
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final StateService stateService;
    private final PasswordEncoder passwordEncoder;
    private final MessageSource messageSource;

    @Autowired
    public UserController(UserService userService, RoleService roleService, StateService stateService, PasswordEncoder passwordEncoder, MessageSource messageSource) {
        this.userService = userService;
        this.roleService = roleService;
        this.stateService = stateService;
        this.passwordEncoder = passwordEncoder;
        this.messageSource = messageSource;
    }

    /**LIST*/
    @PreAuthorize("hasAnyRole('ADMIN')")
    @RequestMapping(value = "/users", method = RequestMethod.GET)
    public String secureListUsers(Model model) {
        model.addAttribute("users", this.userService.getAllUsers());
        return "users";
    }

    /**ADD*/
    @RequestMapping(value= "/user/add", method = RequestMethod.POST)
    public String addUser(HttpServletRequest request, @Valid @ModelAttribute("user") User user,
                          BindingResult bindingResult,
                          @ModelAttribute("autoLogin") boolean autoLogin,
                          RedirectAttributes redir) {

        if (bindingResult.hasErrors()) {
            return "login";
        }

        try {
            //kotroluje jestli už uživatel neexistuje
            Optional<User> exUser = userService.getUserByEmail(user.getEmail());
            if(exUser.isPresent()){
                String message = messageSource.getMessage("error.user.exist", null, LocaleContextHolder.getLocale());
                redir.addFlashAttribute("regErrorMessage", message);
                return "redirect:/login";
            }

            String pwd = user.getPassword();
            user.setPassword(passwordEncoder.encode(pwd));

            this.userService.saveOrUpdate(user);

            if(autoLogin) {
                request.login(user.getEmail(), pwd);
                return "redirect:/orders";
            }
            else
                return "redirect:/orders";
        }
        catch (Exception ex){
            String message = messageSource.getMessage("error.unexpected.while.registration", null, LocaleContextHolder.getLocale());
            redir.addFlashAttribute("regErrorMessage", message);
            return "redirect:/login";
        }
    }

    /**DETAIL*/
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/user/detail/{id}", method = RequestMethod.GET)
    public String detailUser(@PathVariable("id") Long id,
                             @RequestParam("prof") boolean isProfile,
                             HttpServletRequest request,
                             Model model){

        //kontrola práv
        if(request.isUserInRole("ADMIN") || User.getCurrentUser().getId().equals(id)){
            model.addAttribute("isProfile", isProfile);
            User user = userService.getUserById(id).orElse(null);
            model.addAttribute("user", user);
            return "userDetail";
        }
        model.addAttribute("isProfile", false);

        return "errors/403";
    }

    /**PROFILE*/
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public String currentUser(Model model){

        User user = userService.getUserById(User.getCurrentUser().getId()).orElse(null);
        model.addAttribute("user", user);
        model.addAttribute("isProfile", true);

        return "userDetail";
    }

    /**EDIT*/
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/user/edit/{id}", method = RequestMethod.GET)
    public String editUser(@PathVariable("id") Long id, @RequestParam("prof") boolean isProfile, HttpServletRequest request, Model model){

        model.addAttribute("states", stateService.getAllStates());

        //kontrola práv
        if(request.isUserInRole("ADMIN") || User.getCurrentUser().getId().equals(id)){
            model.addAttribute("isProfile", isProfile);
            User user = userService.getUserById(id).orElse(null);
            model.addAttribute("user", user);
            return "userEdit";
        }

        return "errors/403";
    }

    /**UPDATE*/
    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/user/update", method = RequestMethod.POST)
    public String updateUser(@Valid @ModelAttribute("user") User user,
                             @ModelAttribute("newPassword") String newPassword,
                             @ModelAttribute("isProfile") boolean isProfile,
                             HttpServletRequest request,
                             Model model){

        //kontrola práv
        if(request.isUserInRole("ADMIN") || User.getCurrentUser().getId().equals(user.getId()))
        {
            //kotroluje zda neexistuje uživatel se stejným mailem
            Optional<User> exUser = userService.getUserByEmail(user.getEmail());
            if(exUser.isPresent() && !exUser.get().getId().equals(user.getId())){
                model.addAttribute("user", user);
                String message = messageSource.getMessage("error.user.exist", null, LocaleContextHolder.getLocale());
                model.addAttribute("emailErrorMessage", message);
                return "userEdit";
            }
            else {
                Optional<User> origOptUser = userService.getUserById(user.getId(), true);

                if (origOptUser.isPresent()) {
                    User origUser = origOptUser.get();

                    if (passwordEncoder.matches(user.getPassword(), origUser.getPassword())) {

                        if (newPassword.isEmpty()) {
                            user.setPassword(origUser.getPassword());
                        } else {
                            user.setPassword(passwordEncoder.encode(newPassword));
                        }

                        userService.saveOrUpdate(user);

                        if (isProfile)
                            return "redirect:/profile";
                        else
                            return "user/detail" + user.getId();
                    } else {
                        model.addAttribute("user", user);
                        String message = messageSource.getMessage("error.bad.original.password", null, LocaleContextHolder.getLocale());
                        model.addAttribute("passwordErrorMessage", message);
                        return "userEdit";
                    }
                } else {
                    model.addAttribute("user", user);
                    String message = messageSource.getMessage("error.something.is.wrong", null, LocaleContextHolder.getLocale());
                    model.addAttribute("errorMessage", message);
                    return "userEdit";
                }
            }
        }
        else {
            return "errors/403";
        }
    }

    /**DELETE*/
    @PreAuthorize("hasAnyRole('ADMIN')")
    @RequestMapping(value = "/user/delete/{id}", method = RequestMethod.GET)
    public String removeUser(@PathVariable("id") Long id){
        this.userService.removeUser(id);
        return "redirect:/users";
    }
}